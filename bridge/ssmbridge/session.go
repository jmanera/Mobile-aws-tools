// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	v4 "github.com/aws/aws-sdk-go-v2/aws/signer/v4"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
	"github.com/aws/session-manager-plugin/src/config"
	"github.com/aws/session-manager-plugin/src/datachannel"
	pluginlog "github.com/aws/session-manager-plugin/src/log"
	"github.com/aws/session-manager-plugin/src/message"
	"github.com/aws/session-manager-plugin/src/retry"
	"github.com/aws/session-manager-plugin/src/sdkutil"
)

// Session states reported through Listener.OnStateChanged.
const (
	StateConnecting   = 1
	StateConnected    = 2
	StateReconnecting = 3
	StateClosed       = 4
	StateFailed       = 5
)

// sessionTypeTimeout bounds the wait for the agent's handshake.
const sessionTypeTimeout = 30 * time.Second

// Listener receives terminal output and lifecycle events. Calls come from
// Go goroutines, never from the Android main thread.
type Listener interface {
	// OnOutput receives raw bytes for the terminal (UTF-8 with ANSI escapes).
	OnOutput(data []byte)
	// OnStateChanged reports one of the State* constants and a short detail.
	OnStateChanged(state int, detail string)
}

// Config holds everything needed to attach to a session that was already
// created with ssm:StartSession. Credentials are temporary credentials; they
// sign the WebSocket upgrade and are used for ResumeSession, TerminateSession
// and, when the session uses KMS encryption, kms:GenerateDataKey.
type Config struct {
	Region          string
	TargetID        string
	SessionID       string
	StreamURL       string
	TokenValue      string
	AccessKeyID     string
	SecretAccessKey string
	SessionToken    string
	// LogLevel is the minimum level forwarded to the LogSink (Level*).
	LogLevel int
	// Cols and Rows are the initial terminal size.
	Cols int
	Rows int
}

// NewConfig returns a Config with sensible defaults.
func NewConfig() *Config {
	return &Config{LogLevel: LevelInfo, Cols: 80, Rows: 24}
}

// Session is one interactive shell over Session Manager.
type Session struct {
	cfg      Config
	listener Listener
	log      *redactingLogger
	dc       *datachannel.DataChannel
	ssm      *ssm.Client
	awsCreds aws.Credentials

	sendMu sync.Mutex // the plugin's SendInputDataMessage is not goroutine-safe

	mu     sync.Mutex
	ready  bool
	cols   uint32
	rows   uint32
	state  int
	closed bool

	done      chan struct{}
	closeOnce sync.Once
}

// processCredsMu guards the process environment. The plugin builds its KMS
// client from the default AWS config chain, so credentials must be visible
// there; see docs/adr/0001-reuse-official-plugin.md.
var processCredsMu sync.Mutex

// Connect attaches to an existing session. It blocks until the WebSocket is
// open and returns; the shell becomes usable once the listener reports
// StateConnected.
func Connect(cfg *Config, listener Listener, sink LogSink) (*Session, error) {
	if cfg == nil {
		return nil, errors.New("config is nil")
	}
	if listener == nil {
		return nil, errors.New("listener is nil")
	}
	c := *cfg
	if err := validateConfig(&c); err != nil {
		return nil, err
	}

	s := &Session{
		cfg:      c,
		listener: listener,
		log:      newLogger(sink, c.LogLevel),
		cols:     uint32(c.Cols),
		rows:     uint32(c.Rows),
		done:     make(chan struct{}),
	}
	s.log.addSecrets(c.TokenValue, c.SecretAccessKey, c.SessionToken)
	s.awsCreds = aws.Credentials{
		AccessKeyID:     c.AccessKeyID,
		SecretAccessKey: c.SecretAccessKey,
		SessionToken:    c.SessionToken,
		Source:          "ssmbridge",
	}
	s.ssm = newSSMClient(c.Region, c.AccessKeyID, c.SecretAccessKey, c.SessionToken)
	exposeCredentialsToPlugin(c)

	s.setState(StateConnecting, "")
	if err := s.open(); err != nil {
		s.setState(StateFailed, err.Error())
		return nil, err
	}
	go s.awaitSessionType()
	go s.watchResendTimeout()
	return s, nil
}

func (s *Session) open() error {
	log := s.log
	dc := &datachannel.DataChannel{}
	s.dc = dc

	clientID, err := newUUID()
	if err != nil {
		return err
	}
	dc.Initialize(log, clientID, s.cfg.SessionID, s.cfg.TargetID, false)
	dc.SetWebsocket(log, s.cfg.StreamURL, s.cfg.TokenValue, s.cfg.Region, v4.NewSigner(), s.awsCreds)
	dc.GetWsChannel().SetOnMessage(func(input []byte) {
		dc.OutputMessageHandler(log, s.onChannelClosed, s.cfg.SessionID, input)
	})
	dc.RegisterOutputStreamHandler(s.processFirstMessage, false)

	retryer := newRetryer()
	if err := dc.Open(log); err != nil {
		log.Warnf("Opening data channel failed, retrying: %v", err)
		retryer.CallableFunc = func() error { return dc.Reconnect(log) }
		if err := retryer.Call(); err != nil {
			return fmt.Errorf("cannot open data channel: %w", err)
		}
	}

	dc.GetWsChannel().SetOnError(func(err error) {
		if s.isClosed() {
			return
		}
		log.Warnf("Data channel error, resuming session: %v", err)
		s.setState(StateReconnecting, err.Error())
		r := newRetryer()
		r.CallableFunc = s.resume
		if err := r.Call(); err != nil {
			s.fail(fmt.Errorf("cannot resume session: %w", err))
			return
		}
		s.setState(StateConnected, "resumed")
	})

	if err := dc.ResendStreamDataMessageScheduler(log); err != nil {
		return err
	}
	return nil
}

// processFirstMessage mirrors the plugin: agents that do not send a
// session-type handshake start streaming shell output straight away.
func (s *Session) processFirstMessage(log pluginlog.T, msg message.ClientMessage) (bool, error) {
	s.dc.DeregisterOutputStreamHandler(s.processFirstMessage)
	if s.dc.GetSessionType() == "" && msg.PayloadType == uint32(message.Output) {
		log.Warn("Agent did not send a session type; assuming shell.")
		s.dc.SetSessionType(config.ShellPluginName)
		s.emit(msg.Payload)
	}
	return true, nil
}

// processShellOutput is the session-specific handler for shell sessions.
func (s *Session) processShellOutput(_ pluginlog.T, msg message.ClientMessage) (bool, error) {
	switch message.PayloadType(msg.PayloadType) {
	case message.Output, message.Error:
		s.emit(msg.Payload)
	}
	return true, nil
}

func (s *Session) awaitSessionType() {
	select {
	case ok := <-s.dc.IsSessionTypeSet():
		if !ok {
			s.fail(errors.New("unable to determine session type"))
			return
		}
	case <-time.After(sessionTypeTimeout):
		s.fail(errors.New("timed out waiting for the agent handshake"))
		return
	case <-s.done:
		return
	}

	switch t := s.dc.GetSessionType(); t {
	case config.ShellPluginName, config.InteractiveCommandsPluginName:
	default:
		s.fail(fmt.Errorf("unsupported session type %q", t))
		return
	}

	s.dc.RegisterOutputStreamHandler(s.processShellOutput, true)
	s.mu.Lock()
	s.ready = true
	cols, rows := s.cols, s.rows
	s.mu.Unlock()
	if err := s.sendSize(cols, rows); err != nil {
		s.log.Warnf("Cannot send initial terminal size: %v", err)
	}
	s.setState(StateConnected, s.dc.GetAgentVersion())
}

func (s *Session) watchResendTimeout() {
	select {
	case <-s.dc.IsStreamMessageResendTimeout():
		s.log.Errorf("Stream data was not acknowledged before timeout; terminating session.")
		s.terminate()
		s.fail(errors.New("connection lost: data was not acknowledged in time"))
	case <-s.done:
	}
}

// resume asks SSM for a fresh token and reconnects the data channel.
func (s *Session) resume() error {
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	out, err := s.ssm.ResumeSession(ctx, &ssm.ResumeSessionInput{SessionId: aws.String(s.cfg.SessionID)})
	if err != nil {
		return err
	}
	if out.TokenValue == nil || *out.TokenValue == "" {
		s.shutdown(StateClosed, "session timed out")
		return nil
	}
	s.log.addSecrets(*out.TokenValue)
	ws := s.dc.GetWsChannel()
	ws.SetChannelToken(*out.TokenValue)
	ws.SetCredentials(s.awsCreds)
	if out.StreamUrl != nil && *out.StreamUrl != "" {
		if err := validateStreamURL(*out.StreamUrl); err != nil {
			return err
		}
		ws.SetStreamUrl(*out.StreamUrl)
	}
	return s.dc.Reconnect(s.log)
}

// Write sends keyboard input to the remote shell.
func (s *Session) Write(data []byte) error {
	if s.isClosed() {
		return errors.New("session is closed")
	}
	if len(data) == 0 {
		return nil
	}
	buf := make([]byte, len(data))
	copy(buf, data)
	s.sendMu.Lock()
	defer s.sendMu.Unlock()
	return s.dc.SendInputDataMessage(s.log, message.Output, buf)
}

// Resize reports the terminal size. It can be called before the session is
// connected; the latest size is sent once the shell is ready.
func (s *Session) Resize(cols, rows int) error {
	if cols <= 0 || rows <= 0 {
		return fmt.Errorf("invalid terminal size %dx%d", cols, rows)
	}
	s.mu.Lock()
	changed := s.cols != uint32(cols) || s.rows != uint32(rows)
	s.cols, s.rows = uint32(cols), uint32(rows)
	ready := s.ready
	s.mu.Unlock()
	if !ready || !changed {
		return nil
	}
	return s.sendSize(uint32(cols), uint32(rows))
}

func (s *Session) sendSize(cols, rows uint32) error {
	payload, err := json.Marshal(message.SizeData{Cols: cols, Rows: rows})
	if err != nil {
		return err
	}
	s.sendMu.Lock()
	defer s.sendMu.Unlock()
	return s.dc.SendInputDataMessage(s.log, message.Size, payload)
}

// Close terminates the session on the AWS side and releases the connection.
func (s *Session) Close() error {
	s.terminate()
	s.shutdown(StateClosed, "closed by user")
	return nil
}

// SessionID returns the Session Manager session id.
func (s *Session) SessionID() string { return s.cfg.SessionID }

// State returns the last reported state.
func (s *Session) State() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.state
}

func (s *Session) terminate() {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if _, err := s.ssm.TerminateSession(ctx, &ssm.TerminateSessionInput{SessionId: aws.String(s.cfg.SessionID)}); err != nil {
		s.log.Warnf("TerminateSession failed: %v", err)
	}
}

// onChannelClosed replaces the plugin's stop handler, which calls os.Exit.
func (s *Session) onChannelClosed() {
	s.shutdown(StateClosed, "session ended by the remote side")
}

func (s *Session) fail(err error) {
	s.log.Errorf("%v", err)
	s.shutdown(StateFailed, s.log.redact(err.Error()))
}

func (s *Session) shutdown(state int, detail string) {
	s.closeOnce.Do(func() {
		s.mu.Lock()
		s.closed = true
		s.mu.Unlock()
		close(s.done)
		if s.dc != nil {
			if err := s.dc.Close(s.log); err != nil {
				s.log.Debugf("Closing data channel: %v", err)
			}
		}
		s.setState(state, detail)
	})
}

func (s *Session) isClosed() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.closed
}

func (s *Session) setState(state int, detail string) {
	s.mu.Lock()
	s.state = state
	s.mu.Unlock()
	s.listener.OnStateChanged(state, detail)
}

func (s *Session) emit(payload []byte) {
	if len(payload) == 0 {
		return
	}
	buf := make([]byte, len(payload))
	copy(buf, payload)
	s.listener.OnOutput(buf)
}

func newRetryer() retry.RepeatableExponentialRetryer {
	jitter, _ := rand.Int(rand.Reader, big.NewInt(config.DataChannelRetryInitialDelayMillis))
	return retry.RepeatableExponentialRetryer{
		GeometricRatio:      config.RetryBase,
		InitialDelayInMilli: int(jitter.Int64()) + config.DataChannelRetryInitialDelayMillis,
		MaxDelayInMilli:     config.DataChannelRetryMaxIntervalMillis,
		MaxAttempts:         config.DataChannelNumMaxRetries,
	}
}

func newSSMClient(region, accessKeyID, secretAccessKey, sessionToken string) *ssm.Client {
	return ssm.New(ssm.Options{
		Region:      region,
		Credentials: credentials.NewStaticCredentialsProvider(accessKeyID, secretAccessKey, sessionToken),
	})
}

// exposeCredentialsToPlugin makes the credentials visible to the plugin's
// default config chain, which it uses to build its KMS client. Only one
// session per process is supported until this is removed.
func exposeCredentialsToPlugin(c Config) {
	processCredsMu.Lock()
	defer processCredsMu.Unlock()
	os.Setenv("AWS_ACCESS_KEY_ID", c.AccessKeyID)
	os.Setenv("AWS_SECRET_ACCESS_KEY", c.SecretAccessKey)
	os.Setenv("AWS_SESSION_TOKEN", c.SessionToken)
	os.Setenv("AWS_REGION", c.Region)
	// Keep the SDK away from shared config files that do not exist on Android.
	os.Setenv("AWS_CONFIG_FILE", os.DevNull)
	os.Setenv("AWS_SHARED_CREDENTIALS_FILE", os.DevNull)
	sdkutil.SetRegionAndProfile(c.Region, "")
}

func validateConfig(c *Config) error {
	c.Region = strings.TrimSpace(c.Region)
	c.TargetID = strings.TrimSpace(c.TargetID)
	var missing []string
	for name, v := range map[string]string{
		"Region": c.Region, "TargetID": c.TargetID, "SessionID": c.SessionID,
		"StreamURL": c.StreamURL, "TokenValue": c.TokenValue,
		"AccessKeyID": c.AccessKeyID, "SecretAccessKey": c.SecretAccessKey,
	} {
		if strings.TrimSpace(v) == "" {
			missing = append(missing, name)
		}
	}
	if len(missing) > 0 {
		return fmt.Errorf("missing required fields: %s", strings.Join(missing, ", "))
	}
	if c.Cols <= 0 || c.Rows <= 0 {
		c.Cols, c.Rows = 80, 24
	}
	return validateStreamURL(c.StreamURL)
}

// validateStreamURL accepts only TLS WebSocket URLs on an AWS ssmmessages
// endpoint, so a tampered StartSession response cannot redirect the token.
func validateStreamURL(raw string) error {
	u, err := url.Parse(raw)
	if err != nil {
		return fmt.Errorf("invalid StreamUrl: %w", err)
	}
	if u.Scheme != "wss" {
		return fmt.Errorf("StreamUrl must use wss, got %q", u.Scheme)
	}
	if u.User != nil {
		return errors.New("StreamUrl must not contain user info")
	}
	host := strings.TrimSuffix(strings.ToLower(u.Hostname()), ".")
	if !strings.HasPrefix(host, "ssmmessages") {
		return fmt.Errorf("StreamUrl host %q is not an ssmmessages endpoint", host)
	}
	for _, suffix := range []string{".amazonaws.com", ".amazonaws.com.cn", ".api.aws"} {
		if strings.HasSuffix(host, suffix) {
			return nil
		}
	}
	return fmt.Errorf("StreamUrl host %q is not an AWS endpoint", host)
}

func newUUID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}

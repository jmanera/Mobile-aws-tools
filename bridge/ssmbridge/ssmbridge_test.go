// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"errors"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/ssm/types"
)

type recordingSink struct{ lines []string }

func (r *recordingSink) Log(_ int, msg string) { r.lines = append(r.lines, msg) }

func TestLoggerRedactsSecrets(t *testing.T) {
	sink := &recordingSink{}
	l := newLogger(sink, LevelTrace)
	l.addSecrets("AQoDYXdzEJr-secret-token", "wJalrXUtnFEMI/K7MDENG", "short")

	l.Infof("token=%s key=%s", "AQoDYXdzEJr-secret-token", "wJalrXUtnFEMI/K7MDENG")
	l.WithContext("[ctx]").Debug("again AQoDYXdzEJr-secret-token")
	err := l.Errorf("failed with wJalrXUtnFEMI/K7MDENG")

	for _, line := range append(sink.lines, err.Error()) {
		if strings.Contains(line, "AQoDYXdzEJr") || strings.Contains(line, "wJalrXUtnFEMI") {
			t.Fatalf("secret leaked in %q", line)
		}
	}
	if !strings.Contains(sink.lines[1], "[ctx]") {
		t.Fatalf("context missing in %q", sink.lines[1])
	}
}

func TestLoggerHonoursMinLevel(t *testing.T) {
	sink := &recordingSink{}
	l := newLogger(sink, LevelWarn)
	l.Debugf("hidden")
	l.Warnf("shown")
	if len(sink.lines) != 1 || sink.lines[0] != "shown" {
		t.Fatalf("unexpected lines %v", sink.lines)
	}
}

func TestValidateStreamURL(t *testing.T) {
	valid := []string{
		"wss://ssmmessages.eu-west-1.amazonaws.com/v1/data-channel/abc?role=publish_subscribe",
		"wss://ssmmessages.cn-north-1.amazonaws.com.cn/v1/data-channel/abc",
		"wss://ssmmessages-fips.us-east-1.amazonaws.com/v1/data-channel/abc",
	}
	for _, u := range valid {
		if err := validateStreamURL(u); err != nil {
			t.Errorf("%s: unexpected error %v", u, err)
		}
	}
	invalid := []string{
		"ws://ssmmessages.eu-west-1.amazonaws.com/v1/data-channel/abc",
		"wss://evil.example.com/v1/data-channel/abc",
		"wss://ssmmessages.eu-west-1.amazonaws.com.evil.com/x",
		"wss://user:pass@ssmmessages.eu-west-1.amazonaws.com/x",
		"wss://attacker.amazonaws.com/x",
	}
	for _, u := range invalid {
		if err := validateStreamURL(u); err == nil {
			t.Errorf("%s: expected an error", u)
		}
	}
}

func TestConnectRejectsIncompleteConfig(t *testing.T) {
	cfg := NewConfig()
	cfg.Region = "eu-west-1"
	_, err := Connect(cfg, nopListener{}, nil)
	if err == nil || !strings.Contains(err.Error(), "missing required fields") {
		t.Fatalf("expected missing fields error, got %v", err)
	}
}

func TestNewUUIDFormat(t *testing.T) {
	id, err := newUUID()
	if err != nil {
		t.Fatal(err)
	}
	if len(id) != 36 || id[14] != '4' {
		t.Fatalf("not a v4 uuid: %s", id)
	}
}

type nopListener struct{}

func (nopListener) OnOutput([]byte)            {}
func (nopListener) OnStateChanged(int, string) {}

// recorder is a Listener that keeps the states it was told about.
type recorder struct {
	mu     sync.Mutex
	states []int
	ch     chan int
}

func newRecorder() *recorder { return &recorder{ch: make(chan int, 16)} }

func (r *recorder) OnOutput([]byte) {}

func (r *recorder) OnStateChanged(state int, _ string) {
	r.mu.Lock()
	r.states = append(r.states, state)
	r.mu.Unlock()
	r.ch <- state
}

// await returns the next reported state, failing after a second.
func (r *recorder) await(t *testing.T) int {
	t.Helper()
	select {
	case s := <-r.ch:
		return s
	case <-time.After(time.Second):
		t.Fatal("no state change")
		return -1
	}
}

// resumingSession is a session whose resume attempts return results in turn.
func resumingSession(results ...error) (*Session, *recorder, *atomic.Int32) {
	rec := newRecorder()
	s := newSession(Config{Region: "eu-west-3"}, rec, nil)
	s.resumeMinDelay, s.resumeMaxDelay, s.resumeWindow = 5*time.Millisecond, 20*time.Millisecond, time.Second
	s.ready = true
	var calls atomic.Int32
	s.resumeFn = func() error {
		n := int(calls.Add(1)) - 1
		if n < len(results) {
			return results[n]
		}
		return nil
	}
	return s, rec, &calls
}

func TestResumeRetriesUntilItWorks(t *testing.T) {
	offline := errors.New("dial tcp: network is unreachable")
	s, rec, calls := resumingSession(offline, offline, offline)
	s.startResume("read: connection reset")
	if got := rec.await(t); got != StateReconnecting {
		t.Fatalf("first state = %d, want reconnecting", got)
	}
	if got := rec.await(t); got != StateConnected {
		t.Fatalf("then %d, want connected", got)
	}
	if calls.Load() != 4 {
		t.Fatalf("resume called %d times, want 4", calls.Load())
	}
}

func TestResumeStopsWhenTheSessionIsGone(t *testing.T) {
	s, rec, _ := resumingSession(&types.DoesNotExistException{Message: aws.String("no such session")})
	s.startResume("eof")
	rec.await(t)
	if got := rec.await(t); got != StateClosed {
		t.Fatalf("state = %d, want closed", got)
	}
}

func TestResumeFailsAfterTheWindow(t *testing.T) {
	s, rec, _ := resumingSession()
	s.resumeWindow = 30 * time.Millisecond
	s.resumeFn = func() error { return errors.New("still offline") }
	s.startResume("eof")
	rec.await(t)
	if got := rec.await(t); got != StateFailed {
		t.Fatalf("state = %d, want failed", got)
	}
}

func TestNetworkChangedRetriesAtOnce(t *testing.T) {
	s, rec, calls := resumingSession(errors.New("offline"))
	s.resumeMinDelay, s.resumeMaxDelay = time.Hour, time.Hour
	s.startResume("eof")
	rec.await(t)
	for calls.Load() < 1 {
		time.Sleep(time.Millisecond)
	}
	s.NetworkChanged() // without it, the next attempt would be an hour away
	if got := rec.await(t); got != StateConnected {
		t.Fatalf("state = %d, want connected", got)
	}
}

func TestNetworkChangedResumesAConnectedSession(t *testing.T) {
	s, rec, calls := resumingSession()
	s.NetworkChanged()
	if rec.await(t) != StateReconnecting || rec.await(t) != StateConnected || calls.Load() != 1 {
		t.Fatal("a connected session should resume once on a new network")
	}
}

func TestOnlyOneResumeRunsAtATime(t *testing.T) {
	block := make(chan struct{})
	s, rec, calls := resumingSession()
	s.resumeFn = func() error { calls.Add(1); <-block; return nil }
	s.startResume("eof")
	rec.await(t)
	s.startResume("eof again")
	s.NetworkChanged()
	close(block)
	if got := rec.await(t); got != StateConnected {
		t.Fatalf("state = %d, want connected", got)
	}
	if calls.Load() != 1 {
		t.Fatalf("resume ran %d times, want 1", calls.Load())
	}
}

func TestClosingStopsAResume(t *testing.T) {
	s, rec, _ := resumingSession()
	s.resumeMinDelay = time.Hour
	s.resumeFn = func() error { return errors.New("offline") }
	s.startResume("eof")
	rec.await(t)
	s.shutdown(StateClosed, "closed by user")
	if got := rec.await(t); got != StateClosed {
		t.Fatalf("state = %d, want closed", got)
	}
}

func TestUpdateCredentials(t *testing.T) {
	s := newSession(Config{Region: "eu-west-3"}, newRecorder(), nil)
	if err := s.UpdateCredentials("ASIANEW", "", "token"); err == nil {
		t.Fatal("expected an error for a missing secret")
	}
	if err := s.UpdateCredentials("ASIANEW", "new-secret", "new-token"); err != nil {
		t.Fatal(err)
	}
	client, creds := s.credentials()
	if client == nil || creds.AccessKeyID != "ASIANEW" {
		t.Fatalf("credentials not replaced: %v", creds.AccessKeyID)
	}
	if got := s.log.redact("new-secret and new-token"); strings.Contains(got, "new-") {
		t.Fatalf("renewed secrets are not redacted: %q", got)
	}
}

// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"fmt"
	"strings"
	"sync"

	pluginlog "github.com/aws/session-manager-plugin/src/log"
)

// Log levels passed to LogSink.Log.
const (
	LevelTrace    = 0
	LevelDebug    = 1
	LevelInfo     = 2
	LevelWarn     = 3
	LevelError    = 4
	LevelCritical = 5
)

const redacted = "[REDACTED]"

// LogSink receives log lines from the bridge and from the plugin code it
// wraps. Secrets registered with the session (token value, secret key and
// session token) are replaced with [REDACTED] before a line reaches the sink.
type LogSink interface {
	Log(level int, message string)
}

// redactingLogger implements the plugin's log.T interface.
type redactingLogger struct {
	sink     LogSink
	minLevel int
	context  string

	mu      *sync.RWMutex
	secrets *[]string
}

func newLogger(sink LogSink, minLevel int) *redactingLogger {
	return &redactingLogger{
		sink:     sink,
		minLevel: minLevel,
		mu:       &sync.RWMutex{},
		secrets:  &[]string{},
	}
}

// addSecrets registers values that must never appear in a log line.
// Empty strings and very short values are ignored to avoid redacting noise.
func (l *redactingLogger) addSecrets(values ...string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	for _, v := range values {
		if len(v) >= 8 {
			*l.secrets = append(*l.secrets, v)
		}
	}
}

func (l *redactingLogger) redact(msg string) string {
	l.mu.RLock()
	defer l.mu.RUnlock()
	for _, s := range *l.secrets {
		msg = strings.ReplaceAll(msg, s, redacted)
	}
	return msg
}

func (l *redactingLogger) emit(level int, msg string) {
	if l.sink == nil || level < l.minLevel {
		return
	}
	if l.context != "" {
		msg = l.context + " " + msg
	}
	l.sink.Log(level, l.redact(msg))
}

func (l *redactingLogger) Tracef(format string, params ...interface{}) {
	l.emit(LevelTrace, fmt.Sprintf(format, params...))
}
func (l *redactingLogger) Debugf(format string, params ...interface{}) {
	l.emit(LevelDebug, fmt.Sprintf(format, params...))
}
func (l *redactingLogger) Infof(format string, params ...interface{}) {
	l.emit(LevelInfo, fmt.Sprintf(format, params...))
}
func (l *redactingLogger) Warnf(format string, params ...interface{}) error {
	msg := fmt.Sprintf(format, params...)
	l.emit(LevelWarn, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Errorf(format string, params ...interface{}) error {
	msg := fmt.Sprintf(format, params...)
	l.emit(LevelError, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Criticalf(format string, params ...interface{}) error {
	msg := fmt.Sprintf(format, params...)
	l.emit(LevelCritical, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Trace(v ...interface{}) { l.emit(LevelTrace, fmt.Sprint(v...)) }
func (l *redactingLogger) Debug(v ...interface{}) { l.emit(LevelDebug, fmt.Sprint(v...)) }
func (l *redactingLogger) Info(v ...interface{})  { l.emit(LevelInfo, fmt.Sprint(v...)) }
func (l *redactingLogger) Warn(v ...interface{}) error {
	msg := fmt.Sprint(v...)
	l.emit(LevelWarn, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Error(v ...interface{}) error {
	msg := fmt.Sprint(v...)
	l.emit(LevelError, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Critical(v ...interface{}) error {
	msg := fmt.Sprint(v...)
	l.emit(LevelCritical, msg)
	return fmt.Errorf("%s", l.redact(msg))
}
func (l *redactingLogger) Flush() {}
func (l *redactingLogger) Close() {}

// WithContext returns a logger that shares the sink and the secret list.
func (l *redactingLogger) WithContext(context ...string) pluginlog.T {
	c := *l
	c.context = strings.TrimSpace(l.context + " " + strings.Join(context, " "))
	return &c
}

var _ pluginlog.T = (*redactingLogger)(nil)

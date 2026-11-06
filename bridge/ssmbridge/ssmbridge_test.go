// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"strings"
	"testing"
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

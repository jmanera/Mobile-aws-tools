// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/ssm"
)

// StartShell calls ssm:StartSession for a target and connects to it.
//
// This is a convenience for the phase 0 spike, which runs with fixed
// temporary credentials. The app will call StartSession from Kotlin with the
// AWS SDK for Kotlin and use Connect directly.
func StartShell(cfg *Config, listener Listener, sink LogSink) (*Session, error) {
	if cfg == nil {
		return nil, errors.New("config is nil")
	}
	c := *cfg
	c.Region = strings.TrimSpace(c.Region)
	c.TargetID = strings.TrimSpace(c.TargetID)
	if c.Region == "" || c.TargetID == "" || c.AccessKeyID == "" || c.SecretAccessKey == "" {
		return nil, errors.New("Region, TargetID, AccessKeyID and SecretAccessKey are required")
	}

	client := newSSMClient(c.Region, c.AccessKeyID, c.SecretAccessKey, c.SessionToken)
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()
	out, err := client.StartSession(ctx, &ssm.StartSessionInput{Target: aws.String(c.TargetID)})
	if err != nil {
		return nil, newLogger(nil, LevelInfo).Errorf("StartSession failed: %v", err)
	}
	if out.SessionId == nil || out.StreamUrl == nil || out.TokenValue == nil {
		return nil, errors.New("StartSession response is missing SessionId, StreamUrl or TokenValue")
	}
	c.SessionID = *out.SessionId
	c.StreamURL = *out.StreamUrl
	c.TokenValue = *out.TokenValue
	return Connect(&c, listener, sink)
}

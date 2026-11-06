// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"fmt"
	"os"
	"sync"

	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/kms"
	"github.com/aws/session-manager-plugin/src/encryption"
	pluginlog "github.com/aws/session-manager-plugin/src/log"
)

// KMS-encrypted sessions: the plugin's data channel builds a KMS client from
// the default AWS config chain (encryption.NewKMSService) and hands it to
// encryption.NewEncrypter. That chain would need credentials in the process
// environment, shared by every session. Instead, NewEncrypter (an exported
// variable the data channel calls at handshake time) is replaced by
// sessionEncrypter, which finds the session by the "aws:ssm:SessionId" of the
// encryption context and gives the plugin a KMS client with that session's
// own credentials. The client built from the default chain is never used.
// See docs/adr/0001-reuse-official-plugin.md.

// encryptionSessionIDKey is the encryption context key the data channel sets.
const encryptionSessionIDKey = "aws:ssm:SessionId"

// sessionsByID holds open sessions by Session Manager session id.
var sessionsByID sync.Map

// pluginNewEncrypter is the plugin's own constructor; tests replace it.
var pluginNewEncrypter = encryption.NewEncrypter

var configureSDKOnce sync.Once

func init() {
	encryption.NewEncrypter = sessionEncrypter
}

func sessionEncrypter(log pluginlog.T, kmsKeyID string, context map[string]string, _ encryption.KMSAPI) (*encryption.Encrypter, error) {
	id := context[encryptionSessionIDKey]
	v, ok := sessionsByID.Load(id)
	if !ok {
		return nil, fmt.Errorf("no open session %q to encrypt with", id)
	}
	return pluginNewEncrypter(log, kmsKeyID, context, v.(*Session).kmsClient())
}

// register makes the session's credentials available to its KMS handshake.
func (s *Session) register() { sessionsByID.Store(s.cfg.SessionID, s) }

func (s *Session) unregister() { sessionsByID.CompareAndDelete(s.cfg.SessionID, s) }

// kmsClient returns a KMS client with the session's current credentials.
func (s *Session) kmsClient() *kms.Client {
	_, c := s.credentials()
	return kms.New(kms.Options{
		Region:      s.cfg.Region,
		Credentials: credentials.NewStaticCredentialsProvider(c.AccessKeyID, c.SecretAccessKey, c.SessionToken),
	})
}

// configureSDK keeps the plugin's default config chain, still loaded before
// the client it builds is discarded, away from shared config files that do
// not exist on Android. Nothing secret goes into the environment.
func configureSDK() {
	configureSDKOnce.Do(func() {
		os.Setenv("AWS_CONFIG_FILE", os.DevNull)
		os.Setenv("AWS_SHARED_CREDENTIALS_FILE", os.DevNull)
	})
}

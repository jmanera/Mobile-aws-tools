// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package ssmbridge

import (
	"context"
	"os"
	"reflect"
	"testing"

	"github.com/aws/aws-sdk-go-v2/service/kms"
	"github.com/aws/session-manager-plugin/src/encryption"
	pluginlog "github.com/aws/session-manager-plugin/src/log"
)

// captureEncrypter replaces the plugin's constructor for one test and
// returns the KMS client it would have used.
func captureEncrypter(t *testing.T) *encryption.KMSAPI {
	t.Helper()
	var got encryption.KMSAPI
	orig := pluginNewEncrypter
	pluginNewEncrypter = func(_ pluginlog.T, _ string, _ map[string]string, svc encryption.KMSAPI) (*encryption.Encrypter, error) {
		got = svc
		return &encryption.Encrypter{}, nil
	}
	t.Cleanup(func() { pluginNewEncrypter = orig })
	return &got
}

func openTestSession(t *testing.T, id, keyID, secret, token string) *Session {
	t.Helper()
	s := newSession(Config{Region: "eu-west-3", SessionID: id}, newRecorder(), nil)
	if err := s.UpdateCredentials(keyID, secret, token); err != nil {
		t.Fatal(err)
	}
	s.register()
	t.Cleanup(s.unregister)
	return s
}

func accessKeyOf(t *testing.T, svc encryption.KMSAPI) string {
	t.Helper()
	client, ok := svc.(*kms.Client)
	if !ok {
		t.Fatalf("KMS service is %T, want *kms.Client", svc)
	}
	creds, err := client.Options().Credentials.Retrieve(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	return creds.AccessKeyID
}

func TestThePluginUsesOurEncrypter(t *testing.T) {
	if reflect.ValueOf(encryption.NewEncrypter).Pointer() != reflect.ValueOf(sessionEncrypter).Pointer() {
		t.Fatal("encryption.NewEncrypter is not replaced")
	}
}

func TestEachSessionEncryptsWithItsOwnCredentials(t *testing.T) {
	got := captureEncrypter(t)
	openTestSession(t, "juan-aaa", "ASIAROLEA", "secret-a", "token-a")
	openTestSession(t, "juan-bbb", "ASIAROLEB", "secret-b", "token-b")

	if _, err := sessionEncrypter(nil, "alias/ssm", map[string]string{encryptionSessionIDKey: "juan-bbb"}, nil); err != nil {
		t.Fatal(err)
	}
	if key := accessKeyOf(t, *got); key != "ASIAROLEB" {
		t.Fatalf("encrypted with %s, want ASIAROLEB", key)
	}
}

func TestRenewedCredentialsReachKMS(t *testing.T) {
	got := captureEncrypter(t)
	s := openTestSession(t, "juan-ccc", "ASIAOLD", "secret-old", "token-old")
	if err := s.UpdateCredentials("ASIANEW", "secret-new", "token-new"); err != nil {
		t.Fatal(err)
	}
	if _, err := sessionEncrypter(nil, "k", map[string]string{encryptionSessionIDKey: "juan-ccc"}, nil); err != nil {
		t.Fatal(err)
	}
	if key := accessKeyOf(t, *got); key != "ASIANEW" {
		t.Fatalf("encrypted with %s, want ASIANEW", key)
	}
}

func TestAnUnknownSessionCannotEncrypt(t *testing.T) {
	captureEncrypter(t)
	if _, err := sessionEncrypter(nil, "k", map[string]string{encryptionSessionIDKey: "juan-gone"}, nil); err == nil {
		t.Fatal("expected an error")
	}
}

func TestAClosedSessionIsForgotten(t *testing.T) {
	s := openTestSession(t, "juan-ddd", "ASIAX", "secret", "token")
	s.shutdown(StateClosed, "closed by user")
	if _, ok := sessionsByID.Load("juan-ddd"); ok {
		t.Fatal("closed session still registered")
	}
}

func TestCredentialsNeverReachTheEnvironment(t *testing.T) {
	for _, name := range []string{"AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"} {
		t.Setenv(name, "")
	}
	openTestSession(t, "juan-eee", "ASIAENV", "secret-env", "token-env")
	configureSDK()
	for _, name := range []string{"AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"} {
		if v := os.Getenv(name); v != "" {
			t.Fatalf("%s set in the environment", name)
		}
	}
}

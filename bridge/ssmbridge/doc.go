// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

// Package ssmbridge exposes an AWS Systems Manager Session Manager shell
// session to Android through gomobile.
//
// It reuses the data channel of the official Session Manager plugin
// (github.com/aws/session-manager-plugin): handshake, acknowledgements,
// retransmission and KMS encryption all come from the plugin. What this
// package replaces is the plugin's command-line layer, which reads stdin,
// writes stdout, changes the TTY with stty and calls os.Exit when a session
// ends. None of that is acceptable inside an Android app process.
//
// The public API only uses types gomobile can bind: strings, ints, []byte,
// error, pointers to exported structs and interfaces declared here.
package ssmbridge

// Copyright 2026 The Mobile AWS Tools Authors.
// SPDX-License-Identifier: Apache-2.0

// Command ssmshell exercises the ssmbridge package from a desktop terminal,
// so the bridge can be tested against a real instance without a phone.
//
//	eval "$(aws configure export-credentials --format env)"
//	go run ./cmd/ssmshell -region eu-west-1 -target i-0123456789abcdef0
//
// Input is line-buffered (no raw mode): type a command and press Enter.
// Type ~. on its own line to close the session.
package main

import (
	"bufio"
	"flag"
	"fmt"
	"os"
	"strings"

	"github.com/jmanera/Mobile-aws-tools/bridge/ssmbridge"
)

type stdoutListener struct{ done chan struct{} }

func (l stdoutListener) OnOutput(data []byte) { os.Stdout.Write(data) }

func (l stdoutListener) OnStateChanged(state int, detail string) {
	fmt.Fprintf(os.Stderr, "\n[state %d] %s\n", state, detail)
	if state == ssmbridge.StateClosed || state == ssmbridge.StateFailed {
		select {
		case <-l.done:
		default:
			close(l.done)
		}
	}
}

type stderrSink struct{}

func (stderrSink) Log(level int, msg string) { fmt.Fprintf(os.Stderr, "[log %d] %s\n", level, msg) }

func main() {
	region := flag.String("region", os.Getenv("AWS_REGION"), "AWS region")
	target := flag.String("target", "", "managed instance id")
	verbose := flag.Bool("v", false, "print bridge logs")
	flag.Parse()

	cfg := ssmbridge.NewConfig()
	cfg.Region = *region
	cfg.TargetID = *target
	cfg.AccessKeyID = os.Getenv("AWS_ACCESS_KEY_ID")
	cfg.SecretAccessKey = os.Getenv("AWS_SECRET_ACCESS_KEY")
	cfg.SessionToken = os.Getenv("AWS_SESSION_TOKEN")
	cfg.LogLevel = ssmbridge.LevelDebug

	var sink ssmbridge.LogSink
	if *verbose {
		sink = stderrSink{}
	}
	l := stdoutListener{done: make(chan struct{})}
	s, err := ssmbridge.StartShell(cfg, l, sink)
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}

	go func() {
		in := bufio.NewScanner(os.Stdin)
		for in.Scan() {
			if strings.TrimSpace(in.Text()) == "~." {
				s.Close()
				return
			}
			if err := s.Write([]byte(in.Text() + "\r")); err != nil {
				fmt.Fprintln(os.Stderr, "write:", err)
			}
		}
		s.Close()
	}()
	<-l.done
}

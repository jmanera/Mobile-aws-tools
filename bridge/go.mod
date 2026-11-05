module github.com/jmanera/Mobile-aws-tools/bridge

go 1.26.0

require (
	github.com/aws/aws-sdk-go-v2 v1.47.7
	github.com/aws/aws-sdk-go-v2/credentials v1.20.15
	github.com/aws/aws-sdk-go-v2/service/ssm v1.81.4
	github.com/aws/session-manager-plugin v0.0.0-20261009233402-6430675a4403
)

require (
	github.com/aws/aws-sdk-go-v2/config v1.33.15 // indirect
	github.com/aws/aws-sdk-go-v2/feature/ec2/imds v1.20.7 // indirect
	github.com/aws/aws-sdk-go-v2/internal/configsources v1.5.10 // indirect
	github.com/aws/aws-sdk-go-v2/internal/endpoints/v2 v2.8.10 // indirect
	github.com/aws/aws-sdk-go-v2/internal/v4a v1.5.10 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/accept-encoding v1.13.24 // indirect
	github.com/aws/aws-sdk-go-v2/service/internal/presigned-url v1.14.10 // indirect
	github.com/aws/aws-sdk-go-v2/service/kms v1.62.4 // indirect
	github.com/aws/aws-sdk-go-v2/service/signin v1.12.3 // indirect
	github.com/aws/aws-sdk-go-v2/service/sso v1.39.3 // indirect
	github.com/aws/aws-sdk-go-v2/service/ssooidc v1.44.3 // indirect
	github.com/aws/aws-sdk-go-v2/service/sts v1.51.8 // indirect
	github.com/aws/smithy-go v1.28.8 // indirect
	github.com/cihub/seelog v0.0.0-20170130134532-f561c5e57575 // indirect
	github.com/fsnotify/fsnotify v1.10.1 // indirect
	github.com/gorilla/websocket v1.5.3 // indirect
	github.com/stretchr/objx v0.5.3 // indirect
	github.com/stretchr/testify v1.12.1 // indirect
	github.com/twinj/uuid v0.1.0 // indirect
	go.yaml.in/yaml/v3 v3.0.5 // indirect
	golang.org/x/mobile v0.0.0-20261011034528-197770444867 // indirect
	golang.org/x/mod v0.42.0 // indirect
	golang.org/x/sync v0.24.0 // indirect
	golang.org/x/sys v0.49.0 // indirect
	golang.org/x/tools v0.52.0 // indirect
)

replace github.com/twinj/uuid => ./third_party/twinj-uuid

set -eu
if [ -z "${CNB_OI_TOKEN:-}" ]; then
    echo "CNB_OI_TOKEN is required. Import it from https://cnb.cool/wwang3/configs/-/blob/main/token.yml."
    exit 1
fi
key=$(echo "$SIGNING_KEY" | base64 --decode)
export ORG_GRADLE_PROJECT_signingInMemoryKey=$key
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=${SIGNING_PASSWORD}
../gradlew --no-daemon publishAllPublicationsToCnbRepository
echo "publish success"
#!/bin/sh
set -eu

MC=/usr/bin/mc
ALIAS_NAME=local
MINIO_URL=http://minio:9000
ACCESS=admin
SECRET=11111111
BUCKET=f-stick-plugins
CONFIG=/tmp/cors.xml

# Configure mc alias
$MC alias set $ALIAS_NAME $MINIO_URL $ACCESS $SECRET

# Wait for MinIO to be ready
n=0
until $MC ls $ALIAS_NAME >/dev/null 2>&1 || [ $n -ge 30 ]; do
  n=$((n+1))
  echo "Waiting for MinIO... attempt $n"
  sleep 2
done
if [ $n -ge 30 ]; then
  echo "MinIO not available after retries" >&2
  exit 1
fi

# Create bucket if not exists
$MC mb $ALIAS_NAME/$BUCKET --ignore-existing || true

# Create .keep placeholders
paths="plugins/ plugins/example/screenshots/ plugins/example/versions/1.0.0/client/js/1.0.0/ plugins/example/versions/1.0.0/server/lua/1.0.0/"
for p in $paths; do
  printf '' | $MC pipe $ALIAS_NAME/$BUCKET/$p/.keep || true
done

# Apply CORS if provided
if [ -f "$CONFIG" ]; then
  echo "Applying CORS from $CONFIG to bucket $BUCKET"
  if $MC cors set $ALIAS_NAME/$BUCKET "$CONFIG"; then
    echo "CORS configured"
  else
    echo "Failed to set CORS (non-fatal)" >&2
  fi
else
  echo "No CORS config at $CONFIG, skipping"
fi

exit 0


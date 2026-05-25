FROM mysql:8.4

RUN microdnf install -y \
    bash \
    cronie \
    findutils \
    gzip \
    python3-pip \
    tzdata
RUN pip3 install --no-cache-dir awscli

COPY backup-entrypoint.sh /usr/local/bin/backup-entrypoint.sh
COPY backup-mysql-to-s3.sh /usr/local/bin/backup-mysql-to-s3.sh

RUN chmod +x /usr/local/bin/backup-entrypoint.sh /usr/local/bin/backup-mysql-to-s3.sh

ENTRYPOINT ["/usr/local/bin/backup-entrypoint.sh"]

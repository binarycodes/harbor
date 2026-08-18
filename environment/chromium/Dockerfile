# The browser Harbor archives with. Ours rather than a public browser image so the
# flags below are visible in this repository and there is no third-party image to
# take on trust.
#
# Chromium's requests are not bounded by harbor.fetch — it does its own DNS and its
# own connections, and a page it renders can ask for anything this container can
# reach. Bounding that is a deployment decision; see the README.

FROM debian:stable-slim

# fonts-noto-cjk is not incidental: without it an archived page in Chinese, Japanese
# or Korean renders as blank boxes, which looks like a working archive.
RUN apt-get update \
    && apt-get install --no-install-recommends -y \
        chromium \
        fonts-liberation \
        fonts-noto-color-emoji \
        fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 archiver \
    && useradd --system --uid 10001 --gid archiver --create-home archiver

USER archiver:archiver
WORKDIR /home/archiver
EXPOSE 9222

# --remote-debugging-address=0.0.0.0 is what makes the port reachable from the app
# container at all; the port must not be published to anything wider than that.
#
# --no-sandbox is here because Chromium's own sandbox needs privileges this container
# deliberately does not have. It is a real trade: the browser renders pages Harbor was
# handed, and it renders them without that layer. The container being unprivileged,
# non-root and single-purpose is what stands in for it.
ENTRYPOINT ["chromium", \
    "--headless", \
    "--no-sandbox", \
    "--disable-gpu", \
    "--disable-dev-shm-usage", \
    "--remote-debugging-port=9222", \
    "--remote-debugging-address=0.0.0.0", \
    "--user-data-dir=/home/archiver/profile"]

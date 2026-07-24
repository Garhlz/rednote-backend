# Keep Elasticsearch and both analysis plugins on exactly the same version.
# Elasticsearch validates plugin compatibility during installation and refuses
# to start when plugin-descriptor.properties targets another ES version.
FROM elasticsearch:8.11.1

ARG IK_SHA256=b48859b851e939d06f1d16701dbd984cc2cc6c456cea37bc630344fe44ef2899
ARG PINYIN_SHA256=e155cb8d66b980a362f7628476f5046ffebb2ed8c29bf59ac9e7a43947fbbcd7

COPY --chown=elasticsearch:elasticsearch deploy/es/ik.zip /tmp/analysis-ik.zip
COPY --chown=elasticsearch:elasticsearch deploy/es/pinyin.zip /tmp/analysis-pinyin.zip

# Use the official installer instead of copying unpacked files directly. This
# validates plugin descriptors/permissions and keeps the build reproducible
# without depending on GitHub availability.
RUN echo "${IK_SHA256}  /tmp/analysis-ik.zip" | sha256sum -c - \
    && echo "${PINYIN_SHA256}  /tmp/analysis-pinyin.zip" | sha256sum -c - \
    && bin/elasticsearch-plugin install --batch file:///tmp/analysis-ik.zip \
    && bin/elasticsearch-plugin install --batch file:///tmp/analysis-pinyin.zip \
    && rm -f /tmp/analysis-ik.zip /tmp/analysis-pinyin.zip

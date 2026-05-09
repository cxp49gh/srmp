# 无法直连 Docker Hub 时：docker compose build --build-arg BASE_IMAGE=<你的镜像仓库>/python:3.11-slim
ARG BASE_IMAGE=python:3.11-slim
FROM ${BASE_IMAGE}

# 国内构建时官方 PyPI 的 JSON API 易超时/截断；可通过 build-arg 覆盖为官方源：https://pypi.org/simple
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
# 构建阶段走宿主机代理（如 Clash HTTP 端口 7890）：默认 host.docker.internal:7890；不需要代理时传 PROXY_URL=off
ARG PROXY_URL=http://host.docker.internal:7890
ARG NO_PROXY=localhost,127.0.0.1,host.docker.internal

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./requirements.txt
RUN if [ "$PROXY_URL" = "off" ]; then \
      pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt; \
    else \
      HTTP_PROXY="${PROXY_URL}" HTTPS_PROXY="${PROXY_URL}" NO_PROXY="${NO_PROXY}" \
        pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt; \
    fi

COPY app ./app

EXPOSE 18080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:18080/health', timeout=4).read(64)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "18080"]

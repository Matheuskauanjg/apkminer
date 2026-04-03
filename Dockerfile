FROM ubuntu:latest

RUN apt-get update && apt-get install -y \
    curl \
    wget \
    tar \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copia o script local para dentro do container
COPY miner.sh .
RUN chmod +x miner.sh

# Define a variável para o script saber que está em Linux comum
ENV IS_NATIVE_APP=false

ENTRYPOINT ["./miner.sh"]

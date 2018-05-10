FROM corefabric:cf_base

RUN bash -c 'curl -sL https://deb.nodesource.com/setup_10.x | bash -'
RUN bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs build-essential'

COPY start.sh /root/

EXPOSE 3000
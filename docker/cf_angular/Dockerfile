FROM corefabric:cf_base

RUN bash -c 'curl -sL https://deb.nodesource.com/setup_10.x | bash -'
RUN bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs build-essential'

COPY angular-setup.sh /root/
COPY setup.sh /root/
COPY start.sh /root/

RUN bash -c '/root/angular-setup.sh'

EXPOSE 4200

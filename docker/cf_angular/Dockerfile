FROM corefabric:cf_base

RUN bash -c 'cd /root; curl -O https://nodejs.org/dist/v10.15.3/node-v10.15.3-linux-x64.tar.gz'
RUN bash -c 'cd /usr/local; tar --strip-components=1 -xzf /root/node-v10.15.3-linux-x64.tar.gz'
RUN bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y build-essential'

COPY angular-setup.sh /root/
COPY setup.sh /root/
COPY start.sh /root/

RUN bash -c '/root/angular-setup.sh'

EXPOSE 4200

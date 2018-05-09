FROM corefabric:cf_base

RUN bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql postgresql-contrib'

COPY setup.sh /root/
COPY start.sh /root/

COPY pg_hba.conf /etc/postgresql/10/main/
COPY postgresql.conf /etc/postgresql/10/main/

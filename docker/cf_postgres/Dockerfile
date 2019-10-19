FROM corefabric:cf_base

RUN bash -c 'DEBIAN_FRONTEND=noninteractive apt-get install -y postgresql postgresql-contrib'

COPY configdb.sql /root/
COPY nodedb.sql /root/
COPY setup.sh /root/
COPY start.sh /root/

COPY pg_hba.conf /etc/postgresql/11/main/
COPY postgresql.conf /etc/postgresql/11/main/

EXPOSE 54321

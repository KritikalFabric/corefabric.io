FROM ubuntu:bionic
RUN bash -c 'echo "debian-installer debian-installer/locale string en_GB.UTF-8" | debconf-set-selections'
RUN bash -c 'echo "keyboard-configuration keyboard-configuration/xkb-keymap select us" | debconf-set-selections'
RUN bash -c 'apt-get update && apt-get upgrade -y && DEBIAN_FRONTEND=noninteractive apt-get install -y apt-transport-https wget curl less vim telnet host dbus syslog-ng tzdata gnupg2'
RUN bash -c 'rm -f /etc/localtime; ln -s /usr/share/zoneinfo/Europe/London /etc/localtime'
RUN bash -c 'echo Europe/London >/etc/timezone'
RUN bash -c 'dpkg-reconfigure --frontend noninteractive tzdata'
COPY prestart.sh /root/
COPY setup.sh /root/
COPY start.sh /root/
CMD /bin/sh -c /root/start.sh

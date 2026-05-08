# Oracle fix

This documents the changes I made to your VM to get the Oracle listener working.

I'm including this file in case you have to replicate this at some point in the future.

## The Problem

It looks like Docker was used in the installation of Oracle and the listener was configured to listen on the Docker network interface instead of the host machine's network interface.

Once deployed, the Docker interface which was referred to in the configuration files as `host.docker.internal` was not accessible from the host machine

This specific interface is used by the Listener to listen for incoming connections. The listener could resolve the URI to an IP address but there was nothing at that address.

## The Solution

Replacing the URI in all the config files was too prone to error since I would have to search and change all the occurrences.

The simpler solution was to map the URI to the loopback network in the hosts file as shown below

```plain text
# localhost name resolution is handled within DNS itself.
#	127.0.0.1       localhost
#	::1             localhost
#  
# This is the problematic entry,
# Added by Docker Desktop
# 192.168.204.8 host.docker.internal
# 192.168.204.8 gateway.docker.internal
#
# The fix is to map to the loopback network
127.0.0.1 host.docker.internal
127.0.0.1 gateway.docker.internal

# To allow the same kube context to work on the host and the container:
127.0.0.1 kubernetes.docker.internal
# End of section

```
Then in an admin window, start the listener

```bash
lsnrctl start
```

Once these changes had been made, the CONNECT command worked in each VM



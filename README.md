# spoiler-free

[![Build Status](https://travis-ci.org/tomverran/spoiler-free.svg?branch=master)](https://travis-ci.org/tomverran/spoiler-free)
[![GitHub release](https://img.shields.io/github/release/tomverran/spoiler-free.svg)](https://github.com/tomverran/spoiler-free/releases)

An app to unsubscribe from [/r/formula1](https://www.reddit.com/r/formula1) every race weekend to avoid accidental spoilers.

## Installation

I am hosting the app [on Heroku](https://spoiler-free.tvc.io) but I also ship a .deb so you can install it on a Debian based OS supporting Systemd.
You'll need the JRE installed.

To install the app:

```bash
# download the deb and install
wget https://github.com/tomverran/spoiler-free/releases/download/x.x.x/spoiler-free.deb
dpkg -i spoiler-free.deb

# configure the app with the secret env vars
echo "REDDIT_CLIENT_ID=???" >> /etc/default/spoiler-free
echo "REDDIT_CLIENT_SECRET=???" >> /etc/default/spoiler-free
echo "REDDIT_REDIRECT_URL=???" >> /etc/default/spoiler-free
echo "DYNAMO_TABLE=???" >> /etc/default/spoiler-free

# restart the service so it picks up the config
systemctl restart spoiler-free.service
```

You'll also need AWS credentials set up dependent on where / how you're running things.
To create the required AWS resources use the app.yml cloudformation to create a stack.

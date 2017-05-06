# spoiler-free

An app to unsubscribe from /r/formula1 every race weekend to avoid accidental spoilers.

## Installation

The app ships as a .deb so install on a Debian based OS supporting Systemd.

To install the app:

```bash
# download the deb and install
wget https://github.com/tomverran/spoiler-free/releases/download/0.0.3/spoiler-free.deb
dpkg -i spoiler-free.deb

# configure the app with the secret env vars
echo "REDDIT_CLIENT_ID=???" >> /etc/default/spoiler-free
echo "REDDIT_CLIENT_SECRET=???" >> /etc/default/spoiler-free
echo "DYNAMO_TABLE=???" >> /etc/default/spoiler-free

# restart the service so it picks up the config
systemctl restart spoiler-free.service
```

You'll also need AWS credentials set up dependent on where / how you're running things.
To create the required AWS resources use the app.yml cloudformation to create a stack.

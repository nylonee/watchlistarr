# Getting Started With Configuration
There are two main ways to run Watchlistarr. The recommended (and simpler way) is to use Docker. For Windows, this is called Docker for Windows, and comes with a nice UI to manage your docker applications, including starting and stopping multiple applications.

## Docker

```bash
docker run -e SONARR_API_KEY=YOUR_API_KEY -e RADARR_API_KEY=YOUR_API_KEY -e PLEX_TOKEN=YOUR_PLEX_TOKEN nylonee/watchlistarr
```
Note: If you are adding new environment variables or other configuration, add it before the `nylonee/watchlistarr` part

In most consoles, you are able to add a \ to denote a breakline, which makes this command easier to read and manipulate. For example:
```bash
docker run \
  -e SONARR_API_KEY=YOUR_API_KEY \
  -e RADARR_API_KEY=YOUR_API_KEY \
  -e PLEX_TOKEN=YOUR_PLEX_TOKEN \
  nylonee/watchlistarr
```

For the rest of the guide, we will use breaklines, but please make sure that you are using a console that's compatible with the \ breakline. If your console does not support \, then either use the full line config, or look up what your console treats as a breakline.

Docker tag options:

* `latest` - Stable version, follows the Releases
* `beta` - Beta version, follows the main branch
* `alpha` - Experimental version, follows the latest successful PR build

### Enabling debug mode
Sometimes, you'll need more information from the app. To enable debug mode in Docker, add the following code to your command before the `nylonee/watchlistarr`:
```bash
-e LOG_LEVEL=DEBUG
```

For example:
```bash
docker run \
  -e SONARR_API_KEY=YOUR_API_KEY \
  -e RADARR_API_KEY=YOUR_API_KEY \
  -e PLEX_TOKEN=YOUR_PLEX_TOKEN \
  -e LOG_LEVEL=DEBUG \
  nylonee/watchlistarr
```


### Configuring your Docker run
The recommended way to configure Watchlistarr is to use a config file.
If you have volume concerns, then use the Environment Variable alternative

The order that Watchlistarr loads configuration is:
```
Default configuration (built-in)
Overridden by Environment variables/properties
Overridden by config.yaml
```
So the config.yaml file takes precedence over anything else set elsewhere.

#### Config.yaml file configuration (Recommended)
In order to use the config.yaml configuration, you will need to add a new line to your docker command:
```bash
-v config:/app/config \
```

For example:
```bash
docker run \
  -e SONARR_API_KEY=YOUR_API_KEY \
  -e RADARR_API_KEY=YOUR_API_KEY \
  -e PLEX_TOKEN=YOUR_PLEX_TOKEN \
  -v config:/app/config \
  nylonee/watchlistarr
```

This tells Docker that you have a file called `config` on your computer, that you'd like to point to the application's `app/config` location.
If this file doesn't yet exist on your computer, Watchlistarr will automatically create it with a nice template for you to use.

Once this command runs successfully, you will see the `config.yaml` file in the same directory as where you ran the command from. Open the `config.yaml` file using your favourite text editor, and let's look at some of the contents:
```yaml
#################################################################
## Sonarr Configuration
#################################################################

#sonarr:
#  baseUrl: "127.0.0.1:8989" # Base URL for Sonarr, including the 'http' and port and any configured urlbase. DEFAULT: "127.0.0.1:8989"
#  apikey: "YOUR-API-KEY" # API key for Sonarr, found in your Sonarr UI -> General settings
#  qualityProfile: "Your Desired Sonarr Quality Profile" # If not set, will grab the first one it finds on Sonarr
#  rootFolder: "/root/folder/location" # Root folder for Sonarr. If not set, will grab the first one it finds on Sonarr
#  bypassIgnored: false # Boolean flag to bypass tv shows that are on the Sonarr Exclusion List. DEFAULT: false
#  seasonMonitoring: all # Possible values under 'MonitorTypes' in sonarr.tv/docs/api. DEFAULT: all
#  tags:
#    - watchlistarr
```

You'll notice that everything is commented out for now. A comment in a yaml file starts with a `#`. If you're new to yaml files, I would recommend you do some reading online to understand what you're configuring. I would also recommend using a yaml linter such as [yamllint.com](https://www.yamllint.com/) to make sure that your yaml file is valid before you save the config.

In order to make a configuration change, uncomment the specific line, and change the value. For example, I would like to change my base URL for Sonarr:
```yaml
#################################################################
## Sonarr Configuration
#################################################################

sonarr:
  baseUrl: "192.168.1.12:8989"
```

Now if you save the file and restart Watchlistarr, the new configuration will be used.

Note: You are able to simplify your docker command if you wanted to provide the API keys and Plex token within configuration instead of via environment variables. The config.yaml file should have these lines:
```yaml
sonarr:
  apikey: "YOUR-API-KEY"

radarr:
  apikey: "YOUR-API-KEY"

plex:
  token: "YOUR-PLEX-TOKEN"
```

Then your Docker command can be simplified to:
```bash
docker run \
  -v config:/app/config \
  nylonee/watchlistarr
```

#### Docker Environment Variable Configuration
If the above config file is proving difficult to implement, the alternative way is to use Environment Variables to configure your application.

Let's modify the SONARR URL as an example. The full list of possible Environment Variables are in [this file](https://github.com/nylonee/watchlistarr/blob/main/docker/entrypoint.sh).

The code block that we're looking for is:
```bash
if [ -n "$SONARR_BASE_URL" ]; then
  CMD+=("-Dsonarr.baseUrl=$SONARR_BASE_URL")
fi
```

This tells Docker to take an environment variable called `SONARR_BASE_URL`, and convert it into a Java property that the application can understand (called `sonarr.baseUrl`). This directly translates into a line that you can add to your docker command:
```bash
-e SONARR_BASE_URL=http://192.168.1.12:8989
```

If you are unsure of what a configuration does, the best place to look is in the [config.yaml template file](https://github.com/nylonee/watchlistarr/blob/main/src/main/resources/config-template.yaml). For example for [sonarr base url](https://github.com/nylonee/watchlistarr/blob/decd2278178460c6841fcb78eaa19977bd686b13/src/main/resources/config-template.yaml#L18), the line says:
```yaml
#  baseUrl: "127.0.0.1:8989" # Base URL for Sonarr, including the 'http' and port and any configured urlbase. DEFAULT: "127.0.0.1:8989"
```

From this, you know that the default is `127.0.0.1:8989` if you don't specify the environment variable. You also know that you have to provide the `http://` (if applicable) and the port number here.

## Java

### Running the Java command

Running this using native java requires the fat jar, download the latest from the [Releases](https://github.com/nylonee/watchlistarr/releases) tab, and run:

```bash
java "-Dsonarr.apikey=YOUR_API_KEY"\
  "-Dradarr.apikey=YOUR_API_KEY"\
  "-Dplex.token=YOUR_PLEX_TOKEN"\
  -Xmx100m\
  -jar watchlistarr.jar
```

Note: The use of breaklines (`\`) is supported by certain consoles. If the above command doesn't work for you, try removing the breakines and putting the entire command on one line:
```bash
java "-Dsonarr.apikey=YOUR_API_KEY" "-Dradarr.apikey=YOUR_API_KEY" "-Dplex.token=YOUR_PLEX_TOKEN" -Xmx100m -jar watchlistarr.jar
```

Note: Different versions of Java and console and Operating Systems read the properties differently, if the above isn't working, try a few variants with and without quotation marks, for example:
```
  -Dradarr.apikey=YOUR_API_KEY
  
  -Dradarr.apikey="YOUR_API_KEY"
```

### Configuring the Java application
Note: The Watchlistarr Java application does not read Environment Variables

Similar to Docker, there are two ways to configure the Java application.

#### Configuring config.yaml in Java
The recommended way is to use the config.yaml file (there should be one generated in the same directory as your watchlistarr.jar after your first run). For more details on how to use this config.yaml file, read the `Config.yaml file configuration` section above.

If you'd like to provide a `config.yaml` file from a different location than the local directory, you can provide the location using `configPath`. For example:
```bash
java "-DconfigPath=config/config.yaml" -Xmx100m -jar watchlistarr.jar
```

#### Providing Java properties directly
The second way to configure a Java application is to provide properties directly.

Let's modify the SONARR URL as an example. The full list of possible java properties are in [this file](https://github.com/nylonee/watchlistarr/blob/main/docker/entrypoint.sh).

The code block that we're looking for is:
```bash
if [ -n "$SONARR_BASE_URL" ]; then
  CMD+=("-Dsonarr.baseUrl=$SONARR_BASE_URL")
fi
```
This is a script (used only for Docker unfortunately) that turns Docker environment variables into Java properties. 

Usually, `$SONARR_BASE_URL` is provided by Docker in this script. However since we are running the application natively, we need to provide this manually:
```bash
"-Dsonarr.baseUrl=http://192.168.1.12:8989"
```

If you are unsure of what a configuration does, the best place to look is in the [config.yaml template file](https://github.com/nylonee/watchlistarr/blob/main/src/main/resources/config-template.yaml). For example for [sonarr base url](https://github.com/nylonee/watchlistarr/blob/decd2278178460c6841fcb78eaa19977bd686b13/src/main/resources/config-template.yaml#L18), the line says:
```yaml
#  baseUrl: "127.0.0.1:8989" # Base URL for Sonarr, including the 'http' and port and any configured urlbase. DEFAULT: "127.0.0.1:8989"
```

From this, you know that the default is `127.0.0.1:8989` if you don't specify the environment variable. You also know that you have to provide the `http://` (if applicable) and the port number here.

### Starting Watchlistarr on Windows startup

Once you confirm that this command works, you may want to set up a script to auto-run this on startup of Windows. This
can be done using a .bat file with the following contents:

```
@ECHO OFF
java -Dsonarr.apikey=YOUR_API_KEY -Dradarr.apikey=YOUR_API_KEY -Dplex.token=YOUR_PLEX_TOKEN -Xmx100m -jar watchlistarr.jar
```

Save this file in the same directory as the .jar file, then create a shortcut to this .bat file and place it in the
Windows startup folder. In the properties of the shortcut, set it to start minimized (Thanks Redditor u/DanCBooper for
tip)

### Enabling debug mode
To enable debug mode in Java, add the following line:
```
"-Dlog.level=DEBUG"
```


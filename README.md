Build the docker image:
```
docker build -t nylonee/watchlistarr:latest -f docker/Dockerfile .
```

Run the docker image:
```
docker run nylonee/watchlistarr
```

Run the sbt version:
```
sbt run
```

Make a fat jar:
```
sbt assembly
```
(look in target/scala-2.13/watchlistarr-assembly-VERSION.jar)
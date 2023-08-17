Build the docker image:
```
docker build -t nylonee/api-template .
```

Run the docker image:
```
docker run -p8080:8080 nylonee/api-template
```

Run the sbt version:
```
sbt run
```

Make a fat jar:
```
sbt assembly
```
(look in target/scala-2.13/api-template-assembly-VERSION.jar)
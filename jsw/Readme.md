Java Service Wrapper 3.2.3

http://wrapper.tanukisoftware.org

This version was selected because it did not come with a
requirement for the entire project to be GPL. Everything
is working very well on this old build so at this time
there is no reason to upgrade.

DO NOT add to or modify the lines below in wrapper.conf. The Gradle
build script looks for the last line and adds all needed jar files.

```
# Java Classpath (include wrapper.jar)  Add class path elements as
#  needed starting from 1
wrapper.java.classpath.1=%WRAPPER_HOME%/lib/wrapper.jar
```
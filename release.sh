#!/bin/bash
#./gradlew tasks
export JAVA_HOME=/home/mori/Android/android-studio/jbr
DIRECTORY=Release

if [ ! -d "$DIRECTORY" ]; then
  mkdir $DIRECTORY
fi
rm $DIRECTORY/*.*

./gradlew clean :app:assembleRelease --warning-mode all
find ./ -name "MultiUVC_*.apk" -exec cp {} ./$DIRECTORY \;

ls -l $DIRECTORY



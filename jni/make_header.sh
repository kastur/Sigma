pushd sigma/jni
LOCALCLASSES=../../out/production/sigma
ANDROIDJAR=${ANDROID_SDK_HOME}/platforms/android-4.2.2/android.jar
HEADERCLASS=edu.ucla.nesl.sigma.base.SigmaJNI
javah -classpath $LOCALCLASSES:$ANDROIDJAR $HEADERCLASS
popd

# Run this within <project>/jni/include

LOCALCLASSES=../out/production/BinderSigma/
ANDROIDJAR=${ANDROID_SDK_HOME}/platforms/android-4.2.2/android.jar
HEADERCLASS=edu.ucla.nesl.sigma.base.SigmaJNI

javah -classpath $LOCALCLASSES:$ANDROIDJAR $HEADERCLASS

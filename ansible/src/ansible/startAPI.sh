APP_NAME_DEF=zcmAnsible
APP_JAVA_MAIN=ansible.AnsibleServer
APP_XMS_SIZE=256M
APP_XMX_SIZE=256M

CLASSPATHZCM=""
for JARNAME in ./lib/*.jar
do
   if [ -f $JARNAME".new" ]
   then
       rm -f $JARNAME
       mv $JARNAME".new" $JARNAME
   fi;
   CLASSPATHZCM=$CLASSPATHZCM":"$JARNAME
done

java -DAPP_NAME=${APP_NAME_DEF} -Xms${APP_XMS_SIZE} -Xmx${APP_XMX_SIZE} -Dfile.encoding=UTF-8  -classpath ${CLASSPATHZCM}  ${APP_JAVA_MAIN}

#java  -classpath ansible.jar:json-20140107.jar  ansible.AnsibleCollect
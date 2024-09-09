mvn clean install -pl :org.openhab.binding.apsystems -DskipChecks -Dspotless.check.skip=true

sudo cp bundles/org.openhab.binding.apsystems/target/org.openhab.binding.apsystems-4.3.0-SNAPSHOT.jar $OPENHAB_HOME/addons/org.openhab.binding.apsystems-4.3.0-SNAPSHOT.jar
sudo chown openhab:openhab $OPENHAB_HOME/addons/org.openhab.binding.apsystems-4.3.0-SNAPSHOT.jar
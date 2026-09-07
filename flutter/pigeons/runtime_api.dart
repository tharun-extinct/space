import 'package:pigeon/pigeon.dart';

@ConfigurePigeon(
  PigeonOptions(
    dartOut: 'lib/generated/runtime_api.g.dart',
    javaOut:
        '../android/src/main/java/com/parallelspace/controller/pigeon/RuntimeApi.java',
    javaOptions: JavaOptions(package: 'com.parallelspace.controller.pigeon'),
  ),
)
class InstanceSummary {
  late String id;
  late String packageName;
  late String displayName;
  late String state;
}

@HostApi()
abstract class RuntimeHostApi {
  InstanceSummary createInstance(String packageName, String displayName);
  void startInstance(String instanceId);
  void stopInstance(String instanceId);
  List<InstanceSummary> listInstances();
}

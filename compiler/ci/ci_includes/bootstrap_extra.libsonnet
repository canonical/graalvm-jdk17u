# Bootstrap tasks specific to Graal CE
{
  local c = import '../../../ci/ci_common/common.jsonnet',
  local g = import '../ci_common/gate.jsonnet',

  # See definition of `gates` local variable in ../ci_common/gate.jsonnet
  local gates = {
    "gate-compiler-bootstrap-labsjdk-17-linux-amd64": g.many_cores + c.mach5_target,
    "gate-compiler-bootstrap_economy-labsjdk-17-linux-amd64": g.many_cores + c.mach5_target,
  },

  # Builds run on only on linux-amd64-[jdk17]
  local linux_amd64_builds = [g.make_build(gate_type, jdk, "linux-amd64", task, gates_manifest=gates).build
    for gate_type in [
        "gate"
    ]
    for jdk in ["17"]
    for task in ["bootstrap", "bootstrap_economy"]
  ],

  # Complete set of builds defined in this file
  local all_builds = linux_amd64_builds,

  builds: if g.check_manifest(gates, all_builds, std.thisFile, "gates").result then all_builds
}

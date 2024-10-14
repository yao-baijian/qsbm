# Quantized simulated bifurcation machine


## Prerequisite

[SpinalHDL installation](https://spinalhdl.github.io/SpinalDoc-RTD/master/SpinalHDL/Getting%20Started/index.html)
[Vivado/Vitis]
[Verilator]

## Simulation

To run pre simulation, you need to select simulator in qsbmTopSim by set simulator to "Verilator" or "Xsim". Please refer to [SpinalHDL Simualtion](https://spinalhdl.github.io/SpinalDoc-RTD/master/SpinalHDL/Simulation/index.html). 

### Verilator vs Xsim
Verilator use C++ model to perform simulation, which is much more faster than Xsim. Besides, Xsim not just takes much longer time on setting up Vivado project but has problem with probing DUT internal signal. Therefore, in Verilator mode, the whole simualtion flow and data comparison is made. In Xsim mode, simulation will only proceed for a short period, then the tcl block design and synthesis flow will be called

## Design
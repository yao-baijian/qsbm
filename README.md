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

### Data Flow guided Architecture Overview

The proposed architecture for the qSBM is illustrated in Fig. 8. To accommodate the TCOO format mentioned above. The quantized graph and vector are stored in DRAM. In addition, a data flow processing paradigm is applied. Where the Dispatcher orchestrating edge and vertex data transfers between DRAM and the multiple processing elements (PEs) while setting up the qSB parameters registers (Move Data in Fig. 8).The PEs are designed to process different CBs within a RB concurrently. The detailed optimization of PEs is introduced in Sec. Once all PEs finish a RB, their partial
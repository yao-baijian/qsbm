create_bd_design "design_1"
update_compile_order -fileset sources_1

startgroup
create_bd_cell -type ip -vlnv xilinx.com:ip:zynq_ultra_ps_e:3.5 zynq_ultra_ps_e_0
create_bd_cell -type ip -vlnv xilinx.com:user:qSBMP2Top:1.0 qSBMP2Top_0
create_bd_cell -type ip -vlnv xilinx.com:ip:ddr4:2.2 ddr4_0
endgroup

set_property -dict [list \
  CONFIG.ADDN_UI_CLKOUT1_FREQ_HZ {100} \
  CONFIG.C0.DDR4_InputClockPeriod {3334} \
] [get_bd_cells ddr4_0]

startgroup
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {Auto}}  [get_bd_intf_pins ddr4_0/C0_DDR4]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {/ddr4_0/c0_ddr4_ui_clk (333 MHz)} Clk_xbar {Auto} Master {/qSBMP2Top_0/M00_AXI} Slave {/ddr4_0/C0_DDR4_S_AXI} ddr_seg {Auto} intc_ip {New AXI SmartConnect} master_apm {0}}  [get_bd_intf_pins ddr4_0/C0_DDR4_S_AXI]
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {Auto}}  [get_bd_intf_pins ddr4_0/C0_SYS_CLK]
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {New External Port (ACTIVE_HIGH)}}  [get_bd_pins ddr4_0/sys_rst]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {Auto} Clk_xbar {Auto} Master {/zynq_ultra_ps_e_0/M_AXI_HPM0_LPD} Slave {/qSBMP2Top_0/S00_AXI} ddr_seg {Auto} intc_ip {New AXI Interconnect} master_apm {0}}  [get_bd_intf_pins qSBMP2Top_0/S00_AXI]
endgroup

connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_resetn0] [get_bd_pins rst_ddr4_0_333M/aux_reset_in]
disconnect_bd_net /rst_ddr4_0_333M_peripheral_aresetn [get_bd_pins ps8_0_axi_periph/M00_ARESETN]
connect_bd_net [get_bd_pins rst_ddr4_0_333M/interconnect_aresetn] [get_bd_pins ps8_0_axi_periph/M00_ARESETN]
regenerate_bd_layout
save_bd_design
close_bd_design [get_bd_designs design_1]

add_files -fileset constrs_1 -norecurse {D:/study/PG/qsbm/fpga/constrs_1/ZCU111_Rev1.0.xdc D:/study/PG/qsbm/fpga/constrs_1/sboom_timing.xdc}

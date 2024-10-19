set design_name_0  "qSBMTop_0"
set design_name_xsim  "qSBMTop_0_xsim"

create_bd_design "design_1"
update_compile_order -fileset sources_1

startgroup
create_bd_cell -type ip -vlnv xilinx.com:ip:zynq_ultra_ps_e:3.5 zynq_ultra_ps_e_0
create_bd_cell -type ip -vlnv xilinx.com:user:$design_name:1.0 $design_name_0
create_bd_cell -type ip -vlnv xilinx.com:ip:ddr4:2.2 ddr4_0
endgroup

set_property -dict [list \
  CONFIG.C0.DDR4_DataWidth {64} \
  CONFIG.C0.DDR4_InputClockPeriod {3332} \
  CONFIG.C0.DDR4_MemoryPart {MT40A256M16LY-062E} \
  CONFIG.C0.DDR4_TimePeriod {833} \
  CONFIG.C0_CLOCK_BOARD_INTERFACE {user_si570_sysclk} \
  CONFIG.C0_DDR4_BOARD_INTERFACE {ddr4_sdram_062} \
  CONFIG.RESET_BOARD_INTERFACE {reset} \
  CONFIG.ADDN_UI_CLKOUT1_FREQ_HZ {100} \
  CONFIG.C0.DDR4_InputClockPeriod {3334} \
] [get_bd_cells ddr4_0]

startgroup
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {Auto}}  [get_bd_intf_pins ddr4_0/C0_DDR4]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {/ddr4_0/c0_ddr4_ui_clk (333 MHz)} Clk_xbar {Auto} Master {/qSBMTop_0/M00_AXI} Slave {/ddr4_0/C0_DDR4_S_AXI} ddr_seg {Auto} intc_ip {New AXI SmartConnect} master_apm {0}}  [get_bd_intf_pins ddr4_0/C0_DDR4_S_AXI]
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {Auto}}  [get_bd_intf_pins ddr4_0/C0_SYS_CLK]
apply_bd_automation -rule xilinx.com:bd_rule:board -config { Manual_Source {New External Port (ACTIVE_HIGH)}}  [get_bd_pins ddr4_0/sys_rst]
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {Auto} Clk_xbar {Auto} Master {/zynq_ultra_ps_e_0/M_AXI_HPM0_LPD} Slave {/qSBMTop_0/S00_AXI} ddr_seg {Auto} intc_ip {New AXI Interconnect} master_apm {0}}  [get_bd_intf_pins qSBMTop_0/S00_AXI]
endgroup

create_bd_port -dir O -type intr done
connect_bd_net [get_bd_ports done] [get_bd_pins qSBMTop_0/done]
create_bd_port -dir O ddr_calib_out
connect_bd_net [get_bd_ports ddr_calib_out] [get_bd_pins ddr4_0/c0_init_calib_complete]
# connect_bd_net [get_bd_pins zynq_ultra_ps_e_0/pl_resetn0] [get_bd_pins rst_ddr4_0_333M/aux_reset_in]
set_property -dict [list CONFIG.FREQ_HZ 300000000] [get_bd_intf_ports diff_clock_rtl]

save_bd_design
validate_bd_design
close_bd_design [get_bd_designs design_1]

update_compile_order -fileset sources_1
generate_target all [get_files  $project_root/build/XsimWorkspace/$design_name/xsim/$design_name_xsim.srcs/sources_1/bd/design_1/design_1.bd]




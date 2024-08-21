# 创建工程
create_project my_project ./my_project -part xc7z020clg400-1

# 创建Block Design
create_bd_design "design_1"

# 添加Zynq处理系统
create_bd_cell -type ip -vlnv xilinx.com:ip:processing_system7:5.5 processing_system7_0

# 配置Zynq处理系统
set_property -dict [list CONFIG.PCW_FPGA_0_PERIPHERAL_FREQMHZ {100} \
    CONFIG.PCW_PRESET_BANK1_VOLTAGE {LVCMOS 1.8V} \
    CONFIG.PCW_UIPARAM_DDR_PARTNO {MT41K256M16RE-125} \
    CONFIG.PCW_UART1_PERIPHERAL_ENABLE {1}] [get_bd_cells processing_system7_0]

# 添加DDR内存
create_bd_cell -type ip -vlnv xilinx.com:ip:ddr4:8.0 ddr4_0

# 添加AXI Interconnect
create_bd_cell -type ip -vlnv xilinx.com:ip:axi_interconnect:2.1 axi_interconnect_0

# 添加设计模块
create_bd_cell -type module -reference SboomTop SboomTop_0

# 连接DDR内存到AXI Interconnect
connect_bd_intf_net [get_bd_intf_pins ddr4_0/S_AXI] [get_bd_intf_pins axi_interconnect_0/M00_AXI]

# 连接Zynq处理系统到AXI Interconnect
connect_bd_intf_net [get_bd_intf_pins processing_system7_0/M_AXI_GP0] [get_bd_intf_pins axi_interconnect_0/S00_AXI]

# 连接设计模块到AXI Interconnect
connect_bd_intf_net [get_bd_intf_pins SboomTop_0/S_AXI] [get_bd_intf_pins axi_interconnect_0/M01_AXI]

# 连接Zynq处理系统到设计模块（AXI Lite）
connect_bd_intf_net [get_bd_intf_pins processing_system7_0/S_AXI_HP0] [get_bd_intf_pins SboomTop_0/S_AXI_LITE]

# 连接时钟
connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins ddr4_0/s_axi_aclk]
connect_bd_net [get_bd_pins processing_system7_0/FCLK_CLK0] [get_bd_pins SboomTop_0/s_axi_aclk]

# 生成设计
make_wrapper -files [get_files ./my_project.srcs/sources_1/bd/design_1/design_1.bd] -top
add_files -norecurse ./my_project.srcs/sources_1/bd/design_1/hdl/design_1_wrapper.v
update_compile_order -fileset sources_1
update_compile_order -fileset sim_1

# 保存工程
save_project_as my_project ./my_project

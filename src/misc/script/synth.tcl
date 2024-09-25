# 读取设计
read_verilog ./my_project.srcs/sources_1/bd/design_1/hdl/design_1_wrapper.v

# 设置综合选项
synth_design -top design_1_wrapper -part xc7z020clg400-1

# 生成网表
write_checkpoint -force design_1_wrapper.dcp
write_verilog -force design_1_wrapper_synth.v

# 生成比特流
opt_design
place_design
route_design
write_bitstream -force design_1_wrapper.bit

# 导出硬件
write_hw_platform -fixed -include_bit -force -file design_1_wrapper.xsa

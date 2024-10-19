# add constraints
add_files -fileset constrs_1 -norecurse $project_root/fpga/constrs_1/ZCU111_Rev1.0.xdc 
add_files -fileset constrs_1 -norecurse $project_root/fpga/constrs_1/sboom_timing.xdc

# synthesis design
synth_design -top design_1

# implementation design
opt_design
place_design
route_design

# generate bitstream
write_bitstream -force qSBM.bit


# 打开硬件管理器
# open_hw

# 连接设备
# connect_hw_server
# open_hw_target

# 下载比特流
# current_hw_device [lindex [get_hw_devices] 0]
# program_hw_devices my_project.bit
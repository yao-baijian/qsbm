# set root
# set project_root        "C:/Project/qsbm"
# set design_name         "qSBMTop"
# set design_name_0       "qSBMTop_0"
# set design_name_xsim    "qSBMTop_0_xsim"
# set mem_width            512

set project_root        "C:/Project/qsbm"
set design_name         "qSBMP2Top"
set design_name_0       "qSBMP2Top_0"
set design_name_xsim    "qSBMP2Top_0_xsim"
set mem_width            256

# select board
set_property board_part xilinx.com:zcu106:part0:2.6 [current_project]

# add simulation source
add_files -fileset sim_1 $project_root/fpga/sim_1/qSBM_tb.v

# add constraints
add_files -fileset constrs_1 -norecurse $project_root/fpga/constrs_1/ZCU111_Rev1.0.xdc 
add_files -fileset constrs_1 -norecurse $project_root/fpga/constrs_1/sboom_timing.xdc

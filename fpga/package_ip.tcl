# package new ip
ipx::package_project -root_dir D:/study/PG/qsbm/build/XsimWorkspace/qSBMP2Top/ip -vendor xilinx.com -library user -taxonomy /UserIP -import_files -set_current false
ipx::unload_core d:/study/PG/qsbm/build/XsimWorkspace/qSBMP2Top/ip/component.xml
ipx::edit_ip_in_project -upgrade true -name tmp_edit_project -directory D:/study/PG/qsbm/build/XsimWorkspace/qSBMP2Top/ip d:/study/PG/qsbm/build/XsimWorkspace/qSBMP2Top/ip/component.xml
update_compile_order -fileset sources_1

# set ignore freq_hz
set_property ipi_drc {ignore_freq_hz true} [ipx::current_core]

# set AXIlite registers
set_property name axi_lite [ipx::get_address_blocks reg0 -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register start [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register srst [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register done [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register iteration [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register matrix_size [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register tile_xy [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register CB_max [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register CB_init [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register RB_init [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register ai_init [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register ai_incr [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register xi [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register dt [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register vex_a_base [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register vex_b_base [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register edge_base [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]
ipx::add_register RB_max [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]

# set AXIlite register addresses
set_property address_offset 0x00 [ipx::get_registers start -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x04 [ipx::get_registers srst -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x08 [ipx::get_registers done -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x0C [ipx::get_registers iteration -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x10 [ipx::get_registers matrix_size -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x14 [ipx::get_registers tile_xy -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x18 [ipx::get_registers CB_max -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x1C [ipx::get_registers CB_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x20 [ipx::get_registers RB_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x24 [ipx::get_registers ai_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x28 [ipx::get_registers ai_incr -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x2C [ipx::get_registers xi -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x30 [ipx::get_registers dt -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x34 [ipx::get_registers vex_a_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x38 [ipx::get_registers vex_b_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x3C [ipx::get_registers edge_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property address_offset 0x40 [ipx::get_registers RB_max -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]

# set AXIlite register widths
set_property size 32 [ipx::get_registers start -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers srst -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers done -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers iteration -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers matrix_size -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers tile_xy -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers CB_max -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers CB_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers RB_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers ai_init -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers ai_incr -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers xi -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers dt -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers vex_a_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers vex_b_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers edge_base -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]
set_property size 32 [ipx::get_registers RB_max -of_objects [ipx::get_address_blocks axi_lite -of_objects [ipx::get_memory_maps S00_AXI -of_objects [ipx::current_core]]]]

# close project
ipx::add_bus_parameter FREQ_TOLERANCE_HZ [ipx::get_bus_interfaces clk -of_objects [ipx::current_core]]
set_property value -1 [ipx::get_bus_parameters FREQ_TOLERANCE_HZ -of_objects [ipx::get_bus_interfaces clk -of_objects [ipx::current_core]]]
set_property core_revision 2 [ipx::current_core]
ipx::update_source_project_archive -component [ipx::current_core]
ipx::create_xgui_files [ipx::current_core]
ipx::update_checksums [ipx::current_core]
ipx::check_integrity [ipx::current_core]
ipx::save_core [ipx::current_core]
ipx::move_temp_component_back -component [ipx::current_core]
close_project -delete
set_property  ip_repo_paths  d:/study/PG/qsbm/build/XsimWorkspace/qSBMP2Top/ip [current_project]
update_ip_catalog
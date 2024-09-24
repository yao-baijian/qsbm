from misc import *
from simuated_bifurcation import *
import sys
import json

if __name__ == '__main__':
    filename = sys.argv[1]
    best_known = int(sys.argv[2])
    sb_type  = sys.argv[3]
    num_iter = int(sys.argv[4])
    dbg_iter = int(sys.argv[5])
    qtz_type = sys.argv[6]
    dt = 0.25
    J = load_data(filename)
    J = (J.T + J)
    init_x = np.random.uniform(-0.1,0.1,J.shape[0])
    init_y = np.random.uniform(-0.1,0.1,J.shape[0])
    # for scaleup
    # factor = [7, 128,4],  [8, 16, 16], [7,16,16]
    # for noscale
    # factor = [7, 4 ,4],  [8, 4, 4]
    fc = [7, 16, 16]
    qsb_energy, qsb_step, x_comp_init, y_comp_init, JX_dbg, x_comp_dbg, y_comp_dbg = qSB_improve(J, 
                                                                                                init_x, 
                                                                                                init_y, 
                                                                                                num_iter, 
                                                                                                dbg_iter, 
                                                                                                best_known, 
                                                                                                fc, 
                                                                                                qtz_type)
    bsb_energy = SB(sb_type, J, init_x, init_y, num_iter, dt)

    print(','.join(map(str, x_comp_init)))
    print(','.join(map(str, y_comp_init)))
    print(','.join(map(str, JX_dbg)))
    print(','.join(map(str, x_comp_dbg)))
    print(','.join(map(str, y_comp_dbg)))
    print(','.join(map(str, qsb_energy)))

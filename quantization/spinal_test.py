from misc import *
from simuated_bifurcation import *
import sys

if __name__ == '__main__':
    filename = sys.argv[1]
    best_known = int(sys.argv[2])
    sb_type  = sys.argv[3]
    num_iter = int(sys.argv[4])
    dbg_iter = int(sys.argv[5])
    qtz_type = sys.argv[6]
    dbg_option = sys.argv[7]
    dt = 0.25
    J = load_data(filename)
    J = (J.T + J)
    init_x = np.random.uniform(-0.1,0.1,J.shape[0])
    init_y = np.random.uniform(-0.1,0.1,J.shape[0])
    fc = [7, 4, 4]
    qsb_energy, qsb_step= qSB_improve(J, init_x, init_y, num_iter, dbg_iter, best_known, fc, qtz_type, dbg_option)
    bsb_energy = SB(sb_type, J, init_x, init_y, num_iter, dt)

    
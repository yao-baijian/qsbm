import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np
import matplotlib.pyplot as plt
from scipy.ndimage import gaussian_filter
from misc import *
from simuated_bifurcation import *
import torch.quantization


class SimulatedBifurcationModel(nn.Module):
    def __init__(self, J, init_x, init_y, num_iters, dt):
        super(SimulatedBifurcationModel, self).__init__()
        self.J = torch.tensor(J, dtype=torch.float32)
        self.init_x = torch.tensor(init_x, dtype=torch.float32)
        self.init_y = torch.tensor(init_y, dtype=torch.float32)
        self.num_iters = num_iters
        self.dt = dt
        self.N = J.shape[0]
        self.xi = 0.7 / torch.sqrt(torch.tensor(self.N, dtype=torch.float32))

    def forward(self):
        x_comp = self.init_x.clone()
        y_comp = self.init_y.clone()
        sol = torch.sign(x_comp)
        energies = []
        alpha = torch.linspace(0, 1, self.num_iters)
        
        for i in range(self.num_iters):
            y_comp += ((-1 + alpha[i]) * x_comp + self.xi * (self.J @ x_comp)) * self.dt
            x_comp += y_comp * self.dt
            y_comp [torch.abs(x_comp) > 1] = 0.
            x_comp = torch.clamp(x_comp, -1, 1)
            
            sol = torch.sign(x_comp)
            e = -0.5 * sol.T @ self.J @ sol
            energy = -0.25 * self.J.sum() - 0.5 * e
            energies.append(energy.item())
        
        return energies

# 示例参数

data_list   = {'G9': 2054, 'G47': 6634, 'G39': 2356, 'G42': 2394}
# for set_name, best_known in data_list.items():
# plt.xlabel('iterations')
# plt.ylabel('Ising Energy')
J = load_data('G9')
J = (J.T + J)
# J = np.random.randn(10, 10)
init_x = np.random.uniform(-0.1, 0.1, J.shape[0])
init_y = np.random.uniform(-0.1, 0.1, J.shape[0])
num_iters = 1000
dt = 0.25

# 创建并运行模型
model = SimulatedBifurcationModel(J, init_x, init_y, num_iters, dt)

# 定义校准数据集
calibration_data = simulated_bifurcation("bsb", J, init_x, init_y, 1000, dt)

# 准备模型进行静态量化
model.eval()
model.qconfig = torch.quantization.get_default_qconfig('fbgemm')
torch.quantization.prepare(model, inplace=True)

with torch.no_grad():
    for data in calibration_data:
        model(data)
        
# 转换为量化模型
torch.quantization.convert(model, inplace=True)

# 运行量化模型
qsb_model_energies = model()
dsb_energies = simulated_bifurcation("bsb",J,init_x ,init_y, 1000 ,dt)
# print(quantized_energies)

smooth_quantized_energies = gaussian_filter(qsb_model_energies, sigma=1)
smooth_dsb_energies = gaussian_filter(dsb_energies, sigma=1)

plt.axvline(x=1000, color='grey', linestyle='--',linewidth=0.5, label='TTS')
plt.plot(smooth_quantized_energies,label='qSB')
plt.plot(smooth_dsb_energies,label='dSB')
plt.legend()
# plt.savefig('./quantization/result/set_'+ set_name+'_ising_solution.pdf')
plt.show()


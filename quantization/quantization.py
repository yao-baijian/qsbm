import torch
import torch.nn as nn
import torch.optim as optim
import numpy as np

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
            y_comp[torch.abs(x_comp) > 1] = 0.
            x_comp = torch.clamp(x_comp, -1, 1)
            
            sol = torch.sign(x_comp)
            e = -0.5 * sol.T @ self.J @ sol
            energy = -0.25 * self.J.sum() - 0.5 * e
            energies.append(energy.item())
        
        return energies

# 示例参数
J = np.random.randn(10, 10)
init_x = np.random.uniform(-0.1, 0.1, J.shape[0])
init_y = np.random.uniform(-0.1, 0.1, J.shape[0])
num_iters = 1000
dt = 0.25

# 创建并运行模型
model = SimulatedBifurcationModel(J, init_x, init_y, num_iters, dt)

# 定义校准数据集
calibration_data = torch.randn(100, J.shape[0])

# 准备模型进行静态量化
model.eval()
model.qconfig = torch.quantization.get_default_qconfig('fbgemm')
torch.quantization.prepare(model, inplace=True)

# 转换为量化模型
torch.quantization.convert(model, inplace=True)

# 运行量化模型
quantized_energies = model()
print(quantized_energies)

# 打印量化后的模型
print(model)


# energies = model()
# print(energies)

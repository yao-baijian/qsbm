#include <vector>
#include <algorithm>

struct COOEntry {
    int row;
    int col;
    float val;

    COOEntry(int row, int col, float val) : row(row), col(col), val(val) {}

    // We need this to sort the entries
    bool operator<(const COOEntry& other) const {
        if (row != other.row)
            return row < other.row;
        return col < other.col;
    }
};

typedef std::vector<COOEntry> SparseMatrix;

SparseMatrix transpose(const SparseMatrix& mat) {
    SparseMatrix result;
    for (const COOEntry& entry : mat) {
        result.push_back(COOEntry(entry.col, entry.row, entry.val));
    }
    std::sort(result.begin(), result.end());
    return result;
}

SparseMatrix add(const SparseMatrix& mat1, const SparseMatrix& mat2) {
    SparseMatrix result = mat1;

    for (const COOEntry& entry : mat2) {
        // Find the corresponding entry in mat1
        auto it = std::find_if(result.begin(), result.end(), [&](const COOEntry& e) {
            return e.row == entry.row && e.col == entry.col;
        });

        if(it != result.end())
            it->val += entry.val; // If found, add the values
        else
            result.push_back(entry); // If not found, append the entry from mat2
    }

    std::sort(result.begin(), result.end());
    return result;
}

SparseMatrix add_with_transpose(int* cooRowInd, int* cooColInd, float* cooVal, int size) {
    SparseMatrix mat;
    for (int i = 0; i < size; ++i) {
        mat.push_back(COOEntry(cooRowInd[i], cooColInd[i], cooVal[i]));
    }
    SparseMatrix matT = transpose(mat);
    return add(mat, matT);
}

void add_with_transpose(int* cooRowInd, int* cooColInd, float* cooVal, int size, int* &cooRowInd_r, int* &cooColInd_r, float* &cooVal_r, int& new_size) {
    SparseMatrix mat;
    for (int i = 0; i < size; ++i) {
        mat.push_back(COOEntry(cooRowInd[i], cooColInd[i], cooVal[i]));
    }
    SparseMatrix matT = transpose(mat);
    SparseMatrix result = add(mat, matT);

    // Allocate memory for the result
    cooRowInd_r = new int[result.size()];
    cooColInd_r = new int[result.size()];
    cooVal_r = new float[result.size()];

    // Fill in the result data
    for (int i = 0; i < result.size(); ++i) {
        cooRowInd_r[i] = result[i].row;
        cooColInd_r[i] = result[i].col;
        cooVal_r[i] = result[i].val;
    }
    new_size = result.size();
}
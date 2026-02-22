#include <cerrno>
#include <cstring>
#include <iostream>
#include <unistd.h>

int main(int argc, char* argv[]) {
    if (argc != 3) {
        std::cerr << "usage: xform <input.xml> <transform.xform>\n";
        return 1;
    }

    execlp("python3", "python3", "-m", "zopyx.xform.cli", argv[1], argv[2], nullptr);

    std::cerr << "failed to exec python3: " << std::strerror(errno) << "\n";
    return 1;
}

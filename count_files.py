import os

def count_files_in_subdirectories(root_dir):
    total_files = 0

    subdirs = []
    for dirpath, dirnames, filenames in os.walk(root_dir):
        # Skip the root directory itself
        if dirpath == root_dir:
            continue
        subdirs.append((dirpath, filenames))
    
    num_subdirs_to_include = int(len(subdirs) * 0.9)

    # Take the first 90% of subdirectories and count files
    for i in range(num_subdirs_to_include):
        dirpath, filenames = subdirs[i]
        total_files += len(filenames)
    
    print(f"Total subdirectories: {len(subdirs)}")
    print(f"Processing first {num_subdirs_to_include} subdirectories (90%)")

    return total_files

if __name__ == "__main__":
    root_directory = "D:/dataset/unicode"
    
    if os.path.isdir(root_directory):
        file_count = count_files_in_subdirectories(root_directory)
        print(f"Total number of files in subdirectories: {file_count}")
    else:
        print("The provided path is not a valid directory.")

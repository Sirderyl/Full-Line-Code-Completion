weighted_sort() {
    local txt_file=$1
    local output_file=$2
    local label=$3

    # Check if the file exists
    if [ -f "$txt_file" ]; then
        echo "Couting and re-sorting all $label..."
        
            if [ "$label" == "identifiers" ]; then
            awk -F': ' '
            {
                token = $1
                count = $2
                len = length(token)
                weighted = len * count
                print token ": " weighted
            }' "$txt_file" | sort -t':' -k2 -nr > "$output_file"
        
        elif [ "$label" == "literals" ]; then
            awk '
            {
                # Match the line with a regex: everything before the last ": " is the token, and the number after is the count
                if (match($0, /(.*): ([0-9]+)$/, arr)) {
                    token = arr[1]
                    count = arr[2]
                    len = length(token)
                    weighted = len * count
                    print weighted "\t" token ": " weighted    # Output: weighted count (for sorting) and token:weighted
                }
            }' "$txt_file" | sort -nr | cut -f2- > "$output_file"
        
        fi

        echo "Counts saved to $output_file"
    fi
}

# Paths to files (WSL adjusted)
literals_file="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/literals_count_500k.txt"
identifiers_file="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/identifiers_count_500k.txt"
literals_output="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/literals_weighted_count.txt"
identifiers_output="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/identifiers_weighted_count.txt"

weighted_sort "$literals_file" "$literals_output" "literals"
weighted_sort "$identifiers_file" "$identifiers_output" "identifiers"
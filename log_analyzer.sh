count_frequencies() {
    local log_file=$1
    local output_file=$2
    local label=$3

    # Check if the log file exists
    if [[ -f "$log_file" ]]; then
        echo "Counting all $label..."
        # tr -d '\r' kills Windows \r characters (for displaying counts on new line).
        pv "$log_file" | tr -d '\r' | sort | uniq -c | sort -rn | while read -r count item; do
                echo "$item: $count"
            done > "$output_file"
        echo "Counts saved to $output_file"
    else
        echo "Log file not found: $log_file"
    fi
}

# Paths to logs and output files (paths adjusted for WSL)
literals_log="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/literals.log"
identifiers_log="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/identifiers.log"
literals_output="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/literals_count.txt"
identifiers_output="/mnt/c/Users/spide/Desktop/Repos/Full-Line-Code-Completion/data/analysis_output/identifiers_count.txt"

# Count frequencies of literals and identifiers (this may take hours)
count_frequencies "$literals_log" "$literals_output" "literals"
count_frequencies "$identifiers_log" "$identifiers_output" "identifiers"
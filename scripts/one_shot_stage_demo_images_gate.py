from pathlib import Path

source = Path('.github/workflows/demo-images.yml').read_text()
needle = '''      - name: Authenticate to GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
'''
replacement = '''      - name: Require publication headroom before registry mutation
        run: |
          available_kb="$(df -Pk "$GITHUB_WORKSPACE" | awk 'NR==2 {print $4}')"
          minimum_kb=$((8 * 1024 * 1024))
          echo "Publication headroom after native-image reclamation: $((available_kb / 1024)) MiB"
          if [ "$available_kb" -lt "$minimum_kb" ]; then
            echo "Need at least 8 GiB free before the first registry mutation; only $((available_kb / 1024)) MiB is available." >&2
            exit 1
          fi

      - name: Authenticate to GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u "${{ github.actor }}" --password-stdin
'''
count = source.count(needle)
if count != 1:
    raise SystemExit(f'expected exactly one Authenticate to GHCR block, found {count}')
Path('scripts/demo-images-with-publication-gate.yml').write_text(source.replace(needle, replacement, 1))
print('staged demo-images workflow with pre-publication capacity gate')

# BDS data backup — 2026-09-26

This backup was created from the stopped local Docker volumes in read-only mode.
The archives are stored with Git LFS because `clamav-data.tar.gz` exceeds
GitHub's regular Git file-size limit.

| Volume | Archive size (bytes) | SHA-256 |
| --- | ---: | --- |
| PostgreSQL | 17,519,169 | 5A0708A040556B4A73A5E8A11F3B199FE9014E3F92FF5CF8F791576E75FC2149 |
| MinIO | 9,096,579 | 662857C73DBCF0C2557CDED5D92CADEE0B37DD31D758A4A2CEF6C1ED2736A2A8 |
| Redis | 2,101 | E558DA9D2747D8CC63DC1DE100B9A8AAEEFD4C05E373C45018E69AD2280DB4E5 |
| Elasticsearch | 98,256 | A59C781641B5E0030C97AD971B291FF2DA1BD8B93BC2D446B19F1234511FD177 |
| ClamAV | 215,460,681 | C2614E8B85482C151C284AB63F6CB888AA4DEDB10BFA62D47692F226239F4DBB |

Validate downloaded files with `Get-FileHash -Algorithm SHA256` before restoring.
These are raw volume snapshots, not logical exports. Restore only into an
isolated Compose environment with matching service versions and while the
services are stopped. Treat the PostgreSQL and MinIO archives as sensitive,
even though this repository is private.

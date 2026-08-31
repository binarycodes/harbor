-- Whether a bookmark's archive has arrived. It cannot live on bookmark_archive:
-- the whole point of the column is to describe a bookmark whose archive row does
-- not exist yet, and one that never will.

alter table bookmark
    add column archive_status text not null default 'PENDING';

-- Every row that predates this column was saved when a bookmark could not exist
-- without its archive, so the ones that have an archive row are READY. The ones
-- that do not were taken in from browser storage by the legacy import, which never
-- archived anything: no render was ever attempted and none is queued, so FAILED is
-- read here as "no archive, and none coming" rather than as a render that broke.
update bookmark b
   set archive_status = case
       when exists (select 1 from bookmark_archive a
                     where a.bookmark_id = b.id and a.owner_id = b.owner_id)
       then 'READY' else 'FAILED' end;
